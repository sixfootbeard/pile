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

import static java.lang.invoke.MethodType.*;
import static java.util.Objects.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.core.indy.IndyHelpers.*;

import java.lang.constant.Constable;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.collection.PersistentMap;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.Constants;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MacroEvaluated;
import pile.compiler.Scopes;
import pile.compiler.CompilerState.ClosureRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.macro.SyntaxQuoteForm;
import pile.compiler.sugar.JavaMethodSugar;
import pile.compiler.sugar.StaticFieldDesugarer;
import pile.compiler.sugar.SymbolDesugarer;
import pile.compiler.typed.Any;
import pile.compiler.typed.TypedHelpers;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.Value;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ReferenceBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileException;
import pile.core.exception.PileInternalException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.indy.PileMethodLinker;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.util.ConstantDynamicBootstrap;
import pile.util.InvokeDynamicBootstrap;

public class SymbolForm implements Form {

    private static final Logger LOG = LoggerSupplier.getLogger(SymbolForm.class);

    private static final List<SymbolDesugarer> SUGAR = List.of(new StaticFieldDesugarer(), new JavaMethodSugar());
    
    private static final int CLOSURE_MODIFIERS = ACC_FINAL | ACC_PRIVATE | ACC_SYNTHETIC;


    private static final Type[] EMPTY_TYPE = new Type[0];

    private final Symbol toEval;
    private final Namespace ns;

    public SymbolForm(Object toEval) {
        this(toEval, NAMESPACE.getValue());
    }

    public SymbolForm(Object toEval, Namespace ns) {
        super();
        this.toEval = expectSymbol(toEval);
        this.ns = ns;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState cs) {
        Object maybeNew = desugar(cs, ns, toEval);
        if (maybeNew != toEval) {
            return Compiler.compileDefer(cs, maybeNew);
        }
        ScopeLookupResult slr = cs.getScope().lookupSymbolScope(toEval);
        return compileSLR(ns, cs, toEval, slr);
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        Object maybeNew = desugar(cs, ns, toEval);
        if (maybeNew != toEval) {
            return Compiler.evaluate(cs, maybeNew);        
        }
        
        ScopeLookupResult slr = cs.getScope().lookupSymbolScope(toEval);

        if (slr == null) {
            throw new PileCompileException("Cannot find symbol '" + toEval + "' from namespace: " + ns.getName());
        }

        switch (slr.scope()) {
            case METHOD:
            case METHOD_LET:
            case NAMESPACE_LET:
            case LITERAL:
                return slr.val();
            case NAMESPACE:
                ensure(slr.val() != null, toEval + " is bound to null");
                return ((Value) slr.val()).getValue();

            default:
                throw new PileCompileException("Unsupported symbol scope: " + slr.scope());
        }
    }

    @Override
    public DeferredCompilation macroCompileForm(CompilerState cs, Keyword context) {
        return new DeferredCompilation(TypeTag.SYMBOL, toEval, (compiler) -> {
            MethodVisitor mv = compiler.getCurrentMethodVisitor();
            
            Symbol out = toEval;
                        
            if (SyntaxQuoteForm.SYNTAX_QUOTE_KW.equals(context)) {
                out = resolve(compiler, out);
            }
            
            ConstantDynamic constForm = out.toConst()
                    .orElseThrow(() -> new RuntimeException("Could not create constant form of a symbol"));
            mv.visitLdcInsn(constForm);
            compiler.getMethodStack().push(Symbol.class);
        });
    }

    public MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
        Symbol out = toEval;
        if (SyntaxQuoteForm.SYNTAX_QUOTE_KW.equals(context)) {
            out = resolve(cs, out);
        }
        return new MacroEvaluated(out, false);
    }

    private Symbol resolve(CompilerState cs, Symbol out) {
        if (out.getNamespace() != null) {
            return out;
        }
        ScopeLookupResult slr = cs.getScope().lookupSymbolScope(toEval);
        if (slr == null && out.getName().endsWith("#")) {
            String genSymSuffix = cs.getGenSymSuffix();
            out = out.withName(out.getName() + genSymSuffix);
        }
        if (slr != null) {
            out = out.withNamespace(slr.namespace());
        }
        return out;
    }

    /**
     * Condy for a {@link PileMethodLinker#isFinal(pile.core.Metadata)
     * final}/{@link BindingType#VALUE value}/non-{@link Constants#toConst(Object)
     * constant} binding which we can inline.
     * 
     * @param lookup
     * @param name   The actual name of the symbol in the namespace
     * @param clazz
     * @param nsStr  The {@link Namespace}
     * @return
     */
    @ConstantDynamicBootstrap
    public static Object bootstrap(Lookup lookup, String name, Class<Object> clazz, String nsStr) {
        LOG.trace("Lazily condy linking to final+constable binding: %s/%s", nsStr, name);
        return getValue(nsStr, name).getValue();
    }

    /**
     * Indy for non-final or final/non-{@link BindingType#VALUE value} bindings. A
     * callsite associated with a particular binding. It compiles to a
     * {@link SwitchPoint} guarding a {@link MethodHandles#constant(Class, Object)
     * constant} method handle.
     *
     * @param caller
     * @param symbolName   The name of the symbol in the namespace
     * @param type
     * @param namespaceStr The namespace
     * @return A Callsite which can be invalidated and relinked.
     */
    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String symbolName, MethodType type, String namespaceStr) {
        return new AbstractRelinkingCallSite(type) {
            @Override
            protected MethodHandle findHandle(Object[] args) throws Throwable {
                LOG.trace("(Re)linking to binding: %s/%s", namespaceStr, symbolName);
                var binding = getValue(namespaceStr, symbolName);
                MethodHandle handle = getHandleFromBinding(caller, type, binding);

                SwitchPoint sp = binding.getSwitchPoint();
                if (! PileMethodLinker.isFinal(binding)) {
                    requireNonNull(sp, "Switchpoint for non-final binding must exist");
                    handle = sp.guardWithTest(handle, relink);
                }
                return handle;
            }

            private MethodHandle getHandleFromBinding(Lookup caller, MethodType type, Binding binding)
                    throws NoSuchMethodException, IllegalAccessException {
                
                BindingType btype = Binding.getType(binding);
                MethodHandle handle = switch (btype) {
                    case VALUE -> {
                        var value = binding.getValue();
                        yield MethodHandles.constant(value.getClass(), value).asType(type);
                    }
                    case DYNAMIC, SCOPED -> {
                        MethodHandle virt = caller.findVirtual(Binding.class, "getValue", methodType(Object.class));
                        handle = virt.bindTo(binding);
                        yield handle.asType(type);
                    }
                    default -> throw new PileInternalException("Unexpected type:" + btype); 

                };

                return handle;
            }
        };
    }

    @ConstantDynamicBootstrap
    public static Symbol mbootstrap(Lookup lookup, String ig, Class<Symbol> clazz, String ns, String name,
            PersistentMap<?, ?> meta) {
        return new Symbol(ns, name).withMeta(meta);
    }

    static DeferredCompilation compileSLR(Namespace ns, CompilerState compilerState, Symbol sym,
            ScopeLookupResult slr) {
            
        if (slr == null) {
            throw new PileCompileException("Not a scoped symbol or class name: " + sym, LexicalEnvironment.extract(sym));
        }
                
        switch (slr.scope()) {
            case METHOD: {
                return defer(slr, (cs) -> {
                    int localSlot = slr.index();
                    if (localSlot == -1) {
                        cs.getCurrentGeneratorAdapter().loadThis();
                    } else {
                        cs.getCurrentGeneratorAdapter().loadArg(localSlot);
                    }
                    cs.getMethodStack().push(slr.type());
                });
            }
            case METHOD_LET: {
                return defer(slr, (cs) -> {
//                    System.out.println("Loading '" + sym + "' [" + slr.index() +  "] " + slr.type());
                    int localSlot = slr.index();
                    cs.getCurrentGeneratorAdapter().loadLocal(localSlot, Type.getType(slr.type()));
                    cs.getMethodStack().push(slr.type());
                });
            }
            case FIELD: {
                return defer(slr, (cs) -> {
                    MethodVisitor methodVisitor = cs.getCurrentMethodVisitor();
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitFieldInsn(GETFIELD, cs.getClassName(), slr.sym(),
                            Type.getDescriptor(toCompilableType(slr.type())));

                    cs.getMethodStack().push(slr.type());

                });
            }
            case NAMESPACE_LET:
            case CLOSURE:
                // TODO Constant forms for this deferred comp
                return defer(slr, (cs) -> {
                    ClassVisitor visitor = cs.getCurrentVisitor();

                    Map<String, ClosureRecord> closureSymbols = cs.getClosureSymbols();
                    String fieldName;
                    Class<?> type;
                    ClosureRecord cr = closureSymbols.get(slr.sym());
                    if (cr == null) {
                        fieldName = "closure$" + slr.sym();
                        type = slr.type(); // null?
                        var compileableType = Helpers.toCompilableType(type);
                        
                        FieldVisitor field = visitor.visitField(CLOSURE_MODIFIERS, fieldName,
                                Type.getDescriptor(compileableType), null, null);
                        field.visitEnd();
                        cs.addClosureSymbol(slr.sym(), fieldName, type);
                    } else {
                        fieldName = cr.memberName();
                        type = slr.type();
                    }

                    var compileableType = Helpers.toCompilableType(type);

                    MethodVisitor methodVisitor = cs.getCurrentMethodVisitor();
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitFieldInsn(GETFIELD, cs.getClassName(), fieldName,
                            Type.getDescriptor(compileableType));

                    cs.getMethodStack().push(type);

                });    
                
            case NAMESPACE:
                Binding b = (Binding) slr.val();
                Object deref = b.getValue();
                
                // TODO Circular final ref
                boolean isFinal = PileMethodLinker.isFinal(b);
                Optional<?> maybeSimpleConst = Constants.toConst(deref);
                
                Optional maybeLdc = maybeSimpleConst
                    .filter(c -> isFinal)
                    .filter(c -> Binding.getType(b) == BindingType.VALUE);

                return defer(slr, maybeLdc, (cs) -> {
                    BindingType type = Binding.getType(b);
                    if (type == BindingType.INTRINSIC) {
                        // Intrinsic bindings are not compiled in at this stage, they are simply
                        // returned back to the caller
                        throw error("Don't compile intrinsic bindings: " + b);
                    }
                    
                    Class<?> stackType = Optional.ofNullable(deref)
                            .map(Object::getClass)
                            .filter(c -> isFinal)
                            .filter(c -> Binding.getType(b) == BindingType.VALUE)
                            .orElse((Class) Any.class);
                    
                    var strSym = strSym(sym);
                    var symNs = b.namespace();
                    var isConst = false;
                    MethodVisitor mv = cs.getCurrentMethodVisitor();
                    if (isFinal && type == BindingType.VALUE) {
                        // Constant propagation
                        if (maybeSimpleConst.isPresent()) {
                            isConst = true;
                            // RETHINK Inlining large persistent collections here will just duplicate their
                            // parts in the constant pool of this class. Maybe exclude those form from
                            // consideration of symbol inlining here.
                            
                            // old style ldc
                            mv.visitLdcInsn(maybeSimpleConst.get());
                            // TODO Any more special cases?
                            if (deref instanceof Number) {
                                stackType = Helpers.toPrimitive(deref.getClass());
                            }
                        } else {
                            // RETHINK Do we want to condy mutable java types?
                            
                            // condy for non-typical const
                            var desc = Helpers.getConstantBootstrapDescriptor(OBJECT_TYPE, STRING_TYPE);
                            Handle h = new Handle(H_INVOKESTATIC, Type.getType(SymbolForm.class).getInternalName(),
                                    "bootstrap", desc, false);
                            ConstantDynamic condy = new ConstantDynamic(strSym, getDescriptor(Helpers.toCompilableType(stackType)), h, symNs);
                            mv.visitLdcInsn(condy);
                        }
                    } else {
                        // indy because we might need a relink
                        indy(mv, strSym, SymbolForm.class, Object.class, EMPTY_TYPE, symNs);
                    }

                    cs.getMethodStack().push(stackType, isConst);
                });
            case LITERAL: 
                if (slr.val() instanceof Class cls) {
                    return defer(slr, cs -> {
                        cs.getCurrentMethodVisitor().visitLdcInsn(getType(cls));
                        cs.getMethodStack().pushConstant(Class.class);
                    }); 
                }
            default:
                throw new PileException("Unhandled symbol var scope:" + slr.scope());
        }

    }

    private static DeferredCompilation defer(ScopeLookupResult slr, Consumer<CompilerState> compile) {
        return new DeferredCompilation(TypeTag.SYMBOL, slr, compile);
    }
    
    private static DeferredCompilation defer(ScopeLookupResult slr, Optional<Object> ldcForm, Consumer<CompilerState> compile) {
        return new DeferredCompilation(TypeTag.SYMBOL, slr, ldcForm, compile);
    }

    private static Binding getValue(String nsStr, String name) {
        Namespace ns = RuntimeRoot.get(nsStr);
        Binding binding = Namespace.getIn(ns, name);
        return binding;
    }
    
    private static Object desugar(CompilerState cs, Namespace ns, Symbol input) {
        Object local = input;
        for (var sugar : SUGAR) {
            Optional<Object> desugarSymbol = sugar.desugarSymbol(input, cs, ns);
            if (desugarSymbol.isPresent()) {
                local = desugarSymbol.get();
            }
        }
        return local;
    }

}
