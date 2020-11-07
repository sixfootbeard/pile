/**
 * Copyright 2023 John Hinchberger
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pile.compiler.form;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.VarHandle.AccessMode;
import java.lang.reflect.Field;
import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodStack;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.form.InteropForm.FieldOrMethod;
import pile.compiler.form.InteropForm.InteropFormRecord;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.Var;
import pile.core.binding.Binding;
import pile.core.binding.ImmutableBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.indy.guard.GuardBuilder;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.util.InvokeDynamicBootstrap;

/**
 * Assigns vars and fields
 * <pre>
 * (def a 12)
 * ;; var set
 * (set! a 55)
 * ;; object set (instance)
 * (set! (. object -field) "val")
 * ;; class object set (static)
 * (set! (. ClassName -field) "val")
 * </pre>
 * 
 *
 */
public class SetForm extends AbstractListForm {

    public SetForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    public SetForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        var first = nth(this.form, 1);
        var second = nth(this.form, 2);

        TypeTag tag = getTag(first);
        return switch (tag) {
            case SYMBOL -> compileSymbol(compilerState, expectSymbol(first), second);
            case SEXP -> compileSexpr(compilerState, expectList(first), second);
            default -> throw new PileCompileException("Invalid set! target: " + tag, LexicalEnvironment.extract(form));
        };

    }

    @Override
    public Object evaluateForm(CompilerState compilerState) throws Throwable {
        var first = nth(this.form, 1);
        var second = nth(this.form, 2);

        TypeTag tag = getTag(first);
        switch (tag) {
            case SYMBOL -> evalSymbol(compilerState, expectSymbol(first), second);
            case SEXP -> evalSexpr(compilerState, expectList(first), second);
            default -> throw new PileCompileException("Invalid set! target: " + tag, LexicalEnvironment.extract(form));
        };
        
        return null;
    }

    private void evalSexpr(CompilerState cs, PersistentList expectList, Object ref) throws Throwable {
        InteropFormRecord parseForm = InteropForm.parseForm(cs, ns, expectList);
        ensure(parseForm.fom() == FieldOrMethod.FIELD, "Cannot set! a non-field");

        switch (parseForm.ios()) {
            case INSTANCE: {                
                var base = Compiler.evaluate(cs, parseForm.instanceOrClass());
                var computed = Compiler.evaluate(cs, ref);
                
                Field field = base.getClass().getField(parseForm.fieldOrMethodName());
                field.set(base, computed);
                
                break;
            }
            case STATIC: {                
                Symbol classSym = (Symbol) parseForm.instanceOrClass();
                var staticClass = classSym.getAsClass(ns);
                var computed = Compiler.evaluate(cs, ref);
                
                Field field = staticClass.getField(parseForm.fieldOrMethodName());
                field.set(null, computed);

                break;
            }
            default:
                throw new PileCompileException("Bad interop form", LexicalEnvironment.extract(form));
        }
        
    }

    private void evalSymbol(CompilerState cs, Symbol sym, Object val) throws Throwable {
        ScopeLookupResult slr = cs.getScope().lookupSymbolScope(sym);

        if (slr == null) {
            throw new PileCompileException("Unable to resolve symbol for set! '" + sym + "'",
                    LexicalEnvironment.extract(sym, form));
        } else if (slr.scope() == VarScope.NAMESPACE) {
            Var v = VarForm.getIn(ns, sym.getName());
            Object evaluate = Compiler.evaluate(cs, val);
            v.set(evaluate);            
        } else {
            var msg = "Invalid symbol scope " + slr.scope() + " for set!, must refer to a namespace symbol.";
            throw new PileCompileException(msg, LexicalEnvironment.extract(sym, form));
        }
    }

    private DeferredCompilation compileSexpr(CompilerState state, PersistentList ref, Object val) {
        InteropFormRecord parseForm = InteropForm.parseForm(state, ns, ref);
        ensure(parseForm.fom() == FieldOrMethod.FIELD, "Cannot set! a non-field");

        return new DeferredCompilation(TypeTag.SEXP, null, cs -> {
            MethodVisitor mv = cs.getCurrentMethodVisitor();
            MethodStack stack = cs.getMethodStack();

            switch (parseForm.ios()) {
                case INSTANCE: {
                    // stack: [base, val]
                    Compiler.compile(cs, parseForm.instanceOrClass());
                    Compiler.compile(cs, val);
                    var types = stack.popN(2);

                    indyVarArg(mv, "instance", SetForm.class, "bootstrap", void.class,
                            mapA(types, tr -> getType(tr.javaClass()), Type[]::new), parseForm.fieldOrMethodName());
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    stack.pushNull();

                    break;
                }
                case STATIC: {
                    // stack: [val]
                    Compiler.compile(cs, val);
                    var type = stack.pop();

                    Symbol classSym = (Symbol) parseForm.instanceOrClass();
                    var staticClass = classSym.getAsClass(ns);

                    // bootstrap args: [clazz, fieldName]
                    indyVarArg(mv, "static", SetForm.class, "bootstrap", void.class, new Type[] { getType(type) },
                            staticClass.getName(), parseForm.fieldOrMethodName());
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    stack.pushNull();

                    break;
                }
                default:
                    throw new PileCompileException("Bad interop form", LexicalEnvironment.extract(form));
            }
        });
    }

    private DeferredCompilation compileSymbol(CompilerState cs, Symbol sym, Object val) {
        ScopeLookupResult slr = cs.getScope().lookupSymbolScope(sym);

        if (slr == null) {
            throw new PileCompileException("Unable to resolve symbol for set! '" + sym + "'",
                    LexicalEnvironment.extract(sym, form));
        } else if (slr.scope() == VarScope.NAMESPACE) {
            return new DeferredCompilation(TypeTag.SEXP, null, compilerState -> {
                MethodVisitor mv = compilerState.getCurrentMethodVisitor();
                MethodStack stack = compilerState.getMethodStack();

                Compiler.compile(compilerState, val);
                Class<?> stackTop = stack.pop();
                indyVarArg(mv, "var", SetForm.class, "bootstrap", void.class, types(List.of(stackTop)),
                        slr.namespace(), sym.getName());
                mv.visitInsn(Opcodes.ACONST_NULL);
                stack.pushNull();
            });
        } else {
            throw new PileCompileException(
                    "Invalid symbol scope for set! " + slr.scope() + ", must refer to a namespace symbol.",
                    LexicalEnvironment.extract(sym, form));
        }
    }

    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, Object... args) throws Exception {
        switch (method) {
            case "var": {
                String nsStr = (String) args[0];
                String name = (String) args[1];
                

                Namespace ns = RuntimeRoot.get(nsStr);
                Var v = VarForm.getIn(ns, name);
                
                MethodHandle varSet = caller.findVirtual(Var.class, "set", methodType(void.class, Object.class));
                
                MethodHandle bound = varSet.bindTo(v);

                return new ConstantCallSite(bound.asType(type));
            }
            case "instance": {
                var fieldName = (String) args[0];
                return new AbstractRelinkingCallSite(type) {
                    @Override
                    protected MethodHandle findHandle(Object[] args) throws Throwable {
                        // base, val
                        Object receiver = args[0];
                        if (receiver == null) {
                            MethodHandle exHandle = Helpers.getExceptionHandle(type, NullPointerException.class,
                                    NullPointerException::new, "Cannot access a field with a null receiver");

                            GuardBuilder guard = new GuardBuilder(exHandle, relink, type);
                            guard.guardNull(0);

                            return guard.getHandle();
                        } else {
                            var receiverClass = receiver.getClass();
                            Field f = receiverClass.getField(fieldName);
                            VarHandle varHandle = caller.unreflectVarHandle(f);
                            MethodHandle handle = varHandle.toMethodHandle(AccessMode.SET);

                            GuardBuilder guard = new GuardBuilder(handle, relink, type);
                            guard.guardExact(0, receiverClass);

                            return guard.getHandle();
                        }
                    }
                };
            }
            case "static": {
                // stack: [val]
                // bootstrap args: [class:str, fieldName]
                var clazz = loadClass((String) args[0]);
                var fieldName = (String) args[1];
                Field f = clazz.getField(fieldName);
                VarHandle varHandle = caller.unreflectVarHandle(f);
                MethodHandle handle = varHandle.toMethodHandle(AccessMode.SET);

                return new ConstantCallSite(handle.asType(type));
            }
            default:
                throw new PileInternalException("Bad method:" + method);
        }
    }

}
