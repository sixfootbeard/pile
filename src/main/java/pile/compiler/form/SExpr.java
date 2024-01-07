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
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.nativebase.NativeCore.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MacroEvaluated;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.specialization.ApplyFormSpecialization;
import pile.compiler.specialization.FormSpecialization;
import pile.compiler.specialization.StrCatSpecializer;
import pile.compiler.sugar.InteropMethodCallSugar;
import pile.compiler.sugar.NewInstanceSugar;
import pile.compiler.sugar.SExprDesugarer;
import pile.compiler.sugar.StaticMethodCallSugar;
import pile.compiler.typed.Any;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.PileMethod;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.IntrinsicBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileException;
import pile.core.exception.PileExecutionException;
import pile.core.exception.PileInternalException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.OpaqueFunctionLinker;
import pile.core.indy.PersistentLiteralLinker;
import pile.core.indy.PileMethodLinker;
import pile.core.method.LinkableMethod;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;
import pile.core.parse.TypeTag;
import pile.util.InvokeDynamicBootstrap;

public class SExpr implements Form {

    private static Method PML_METHOD, OL_METHOD;
    
    private static List<SExprDesugarer<PersistentList<Object>>> DESUGAR = new ArrayList<>();

    static {
        try {
            PML_METHOD = PileMethodLinker.class.getMethod("bootstrap", Lookup.class, String.class, MethodType.class,
                    Symbol.class, CompilerFlags.class, long.class, boolean.class);
            OL_METHOD = OpaqueFunctionLinker.class.getMethod("bootstrap", Lookup.class, String.class, MethodType.class,
                    PersistentMap.class, CompilerFlags.class, Integer.TYPE);
        } catch (NoSuchMethodException | SecurityException e) {
            throw shouldNotHappen();
        }
        
        DESUGAR.add(new InteropMethodCallSugar());
        DESUGAR.add(new StaticMethodCallSugar());
        DESUGAR.add(new NewInstanceSugar());        
    }

    // Specializations that take precedence over any other compilation methods
    private static Map<Symbol, Function<PersistentList, FormSpecialization>> SPECIALIZATIONS = Map
            .of(StrCatSpecializer.STR_FN, StrCatSpecializer::new,
                ApplyFormSpecialization.APPLY_SYM, ApplyFormSpecialization::new);


    private final PersistentList form;
    private final Namespace ns;

    public SExpr(PersistentList form) {
        this(form, NAMESPACE.getValue());
    }

    public SExpr(PersistentList form, Namespace ns) {
        super();
        this.form = form;
        this.ns = ns;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState cs) {
        // Macroexpand
        Object expanded = macroExpand(cs, ns, form);
        TypeTag tag = getTag(expanded);
        if (tag != TypeTag.SEXP) {
            // macros can expand into non-sexps
            // (defmacro foo [] "bar")
            // (fn* [] (foo))

            return Compiler.compileDefer(cs, expanded);
        }
        var list = (PersistentList) expanded;
        if (form == list) {
            Object first = first(list);
            DeferredCompilation firstArg = Compiler.compileDefer(cs, first);
            // TODO Propagate first arg?
            return new DeferredCompilation(TypeTag.SEXP, null, s -> compile(s, firstArg));
        } else {
            // If it expanded to not this list then defer to another sexp
            // TODO Maybe make this a loop?
            return new SExpr(list).compileForm(cs);
        }
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {

        Object expanded;
        try {
            expanded = macroExpand(cs, ns, form);
        } catch (Exception e) {
            throw new PileExecutionException("Error while expanding macro", LexicalEnvironment.extract(form), e);
        }
        if (expanded == null) {
            // RETHINK this null escape
            return null;
        }

        TypeTag tag = getTag(expanded);
        if (tag != TypeTag.SEXP) {
            return Compiler.evaluate(cs, expanded);
        }

        PersistentList list = (PersistentList) expanded;

        Object first = first(expanded);
        if (first == null) {
            if (list.count() == 0) {
                return PersistentList.EMPTY;
            } else {
                throw shouldNotHappen("Unexpected first form: " + expanded);
            }
        }

        Object maybeMethod;
        try {
            maybeMethod = Compiler.evaluate(cs, first);
        } catch (Exception e) {
            throw new PileSyntaxErrorException("Invalid callable: " + first, LexicalEnvironment.extract(form), e);
        }
        
        if (maybeMethod instanceof IntrinsicBinding intrinsic) {
            return intrinsic.getCons().apply(list).evaluateForm(cs);
        } else if (maybeMethod instanceof PCall method) {
            return method.invoke(Compiler.evaluateArgs(cs, more(expanded)).toArray());
        }
        
        throw new PileSyntaxErrorException(
                "Uncallable method: " + first + ", type:" + (maybeMethod == null ? "null" : maybeMethod.getClass()),
                LexicalEnvironment.extract(form));
    }

    @Override
    public DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {

        PersistentList list = form;
        Object arg = first(list);
        
        // We have to check the first symbol 
        if (arg instanceof Symbol sym && 
                sym.getNamespace() != null && 
                ParserConstants.PILE_CORE_NS.equals(sym.getNamespace())) {
                
            ScopeLookupResult slr = compilerState.getScope().lookupSymbolScope(sym);
            if (slr != null && 
                    slr.val() instanceof IntrinsicBinding intrinsic && 
                    intrinsic.isMacro()) {
                return intrinsic.getCons().apply(list).compileForm(compilerState);
            }
        }
        
        // TODO Check this list coercion is ok
        CollectionLiteralForm<PersistentList> lf = new CollectionLiteralForm<>(TypeTag.SEXP, list,
                CollectionLiteralForm.LIST_DESCRIPTOR);
        return lf.macroCompileForm(compilerState, context);
    }

    @Override
    public MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
        try {
            Object first = first(form);

            // If first arg is macro sym, use that binding
            if (type(first) == TypeTag.SYMBOL) {
                ScopeLookupResult slr = cs.getScope().lookupSymbolScope((Symbol) first);

                if (slr != null) {
                    if (slr.val() instanceof Binding bind) {
                        if (bind.isMacro()) {
                            List<Object> evaluated = new ArrayList<>();
                            for (Object f : form.pop()) {
                                MacroEvaluated mev = Compiler.macroEval(cs, f, context);
                                if (mev.isUnsplice())
                                    throw new RuntimeException("NYI");
                                evaluated.add(mev.result());
                            }

                            if (bind instanceof IntrinsicBinding ib) {
                                // FIXME this is a hacky way to get the macro intrinsics to work.
                                evaluated.add(0, null); 
                                return ib.getCons().apply(PersistentList.fromList(evaluated)).macroEvaluateForm(cs, context);
                            } else {
                                var out = ((PCall) bind.getValue()).invoke(evaluated.toArray());
                                return new MacroEvaluated(out, false);
                            }

                        }
                    }
                }
            }

            // else return a list:
            CollectionLiteralForm<PersistentList> lf = new CollectionLiteralForm<>(TypeTag.SEXP, form,
                    CollectionLiteralForm.LIST_DESCRIPTOR);
            return lf.macroEvaluateForm(cs, context);
        } catch (Exception e) {
            throw new RuntimeException("Error while evaluating: " + form, e);
        }
    }

    private void compile(CompilerState cs, DeferredCompilation firstArg) {
        MethodVisitor mv = cs.getCurrentMethodVisitor();
        MethodStack methodStack = cs.getMethodStack();
        
        final boolean lastRecur = cs.isCompiledRecurPosition(); 

        if (firstArg.formType() == TypeTag.SYMBOL) {
            ScopeLookupResult slr = (ScopeLookupResult) firstArg.ref();


            // Specializations
            Symbol sym = slr.fullSym();
            if (sym != null) {
                Function<PersistentList, FormSpecialization> specialization = SPECIALIZATIONS.get(sym);
                if (specialization != null) {
                    if (specialization.apply(form).specialize(cs)) {
                        return;
                    }
                    // bail on specializing
                }
            }
            
            // Normal bindings
            if (slr.scope().equals(VarScope.NAMESPACE)) {
                Binding b = (Binding) slr.val();
                
                if (Binding.getType(b) == BindingType.INTRINSIC) {
                    ((IntrinsicBinding) b).getCons().apply(form).compileForm(cs).compile().accept(cs);
                    return;
                }
                
                cs.setCompileRecurPosition(false);
                int formCount = compileArgs(cs, form.pop());
                List<TypeRecord> popped = methodStack.peekN(formCount);
                boolean allConstant = popped.stream().allMatch(TypeRecord::constant);
                compilePMLIndy(methodStack, b, mv, slr.fullSym(), formCount, allConstant);
                cs.setCompileRecurPosition(lastRecur);
                return;
            }

            // fall through
        }
        
        // TODO Add back in support for callable constant: collections, kw, etc.
        
        // Opaque call
        // Compile receiver
        cs.setCompileRecurPosition(false);
        firstArg.compile().accept(cs);
        boolean constantFn = cs.getMethodStack().peekConstant();
        
        Optional<LexicalEnvironment> maybe = LexicalEnvironment.extract(first(form));
        PersistentMap lexMap = maybe.map(LexicalEnvironment::toMap).orElse(PersistentMap.EMPTY);

        // Compiles args
        compileOpaqueIndy(cs, constantFn, lexMap);
        cs.setCompileRecurPosition(lastRecur);
        return;

    }

    private int compileArgs(CompilerState cs, PersistentList<?> args) {
        int formCount = 0;
        for (Object arg : args) {
            Compiler.compile(cs, arg);
            ++formCount;
        }
        return formCount;
    }

    private void compilePMLIndy(MethodStack methodStack, Binding b, MethodVisitor mv, Symbol sym,
            int formCount, boolean allConstant) {
        if (sym == null) {
            requireNonNull(sym, "symbol cannot be null");
        }   
        
        Handle h = new Handle(Opcodes.H_INVOKESTATIC, "pile/core/indy/PileMethodLinker", "bootstrap",
                Type.getMethodDescriptor(PML_METHOD), false);

        Optional<Class<?>> returnType = Optional.empty();
        List<TypeRecord> typeRecords = methodStack.popN(formCount);
        Type[] args = getJavaTypeArray(typeRecords);
        long anyMask = getAnyMask(typeRecords);
        
        // TODO Maybe we don't want to do this
        if (PileMethodLinker.isFinal(b)) {
            if (b.getValue()instanceof PileMethod pm) {
                Class[] classArray = getJavaClassArray(typeRecords);
                MethodType methodType = methodType(Object.class, classArray);
                List<Class<?>> withoutAny = mapL(methodType.parameterList(), Helpers::toCompilableType);
                Optional<Class<?>> rtype = pm.getReturnType(CallSiteType.PLAIN, methodType(Object.class, withoutAny), anyMask);
                returnType = rtype;                
            }
            if (returnType.isEmpty()) {
                returnType = Optional.ofNullable((Class<?>) b.meta().get(PileMethodLinker.RETURN_TYPE_KEY, null));
            }
        }
        Class<?> compilableReturnType = returnType.orElse(Object.class);
        String methodDescriptor = Type.getMethodDescriptor(getType(compilableReturnType), args);
        handleLineNumber(mv, form);
        mv.visitInvokeDynamicInsn("link", methodDescriptor, h, sym.toConst().get(),
                NativeDynamicBinding.COMPLILER_FLAGS.getValue().toCondy(),
                anyMask, BooleanForm.toCondy(allConstant));
        methodStack.push(returnType.orElse(Any.class));
    }

    private void compileOpaqueIndy(CompilerState cs, boolean constantFn, PersistentMap lexMap) {

        MethodStack methodStack = cs.getMethodStack();
        MethodVisitor mv = cs.getCurrentMethodVisitor();

        Handle h = new Handle(Opcodes.H_INVOKESTATIC, "pile/core/indy/OpaqueFunctionLinker", "bootstrap",
                Type.getMethodDescriptor(OL_METHOD), false);
        // TODO opaque symbol type

        int formCount = compileArgs(cs, form.pop());

        Type[] args = getJavaTypeArray(methodStack.popN(formCount + 1));
        String methodDescriptor = Type.getMethodDescriptor(OBJECT_TYPE, args);
        handleLineNumber(mv, form);
        mv.visitInvokeDynamicInsn("opaque", methodDescriptor, h, lexMap.toConst().get(),
                NativeDynamicBinding.COMPLILER_FLAGS.getValue().toCondy(), constantFn ? 1 : 0);
        methodStack.pushAny();
    }

    public static PersistentList desugar(CompilerState cs, Namespace ns, PersistentList result) {
        // TODO Keep going until there are no changes?
        PersistentList<Object> local = result;
        for (var sugar : DESUGAR) {
            Optional<PersistentList<Object>> maybeResult = sugar.desugar(local, cs, ns);
            if (maybeResult.isPresent()) {
                local = maybeResult.get();
            }
        }
        return local;        
    }

    public static Object macroExpandOne(CompilerState cs, Namespace ns, Object toEval) {
    
        TypeTag tag = getTag(toEval);
        if (tag != TypeTag.SEXP) {
            return toEval;
        }
        // TODO We shouldn't need to to-list here
        PersistentList list = Helpers.toList(toEval);
        PersistentList pl = desugar(cs, ns, list);
        Object first = first(pl);
        DeferredCompilation firstArg = Compiler.compileDefer(cs, first);
        if (firstArg.formType() == TypeTag.SYMBOL) {
            var sym = expectSymbol(first);
            ScopeLookupResult slr = (ScopeLookupResult) firstArg.ref();
            if (slr.val() instanceof Binding bind) {
                if (bind.isMacro() && Binding.getType(bind) != BindingType.INTRINSIC) {
                    PCall base = (PCall) bind.getValue();
                    requireNonNull(base, () -> "Cannot expand macro:" + sym);
                    try {
                        return base.invoke(pl.pop().toArray());
                    } catch (Throwable e) {
                        throw new PileException("Error during macro expansion while calling " + slr.namespace() + "/" + slr.sym(), e);
                    }
                }
            }
        }
        return pl;
    }

    public static Object macroExpand(CompilerState compilerState, Namespace ns, Object toEval) {
        for (;;) {
            var out = macroExpandOne(compilerState, ns, toEval);
            if (out == null) {
                // RETHINK If this null escape will cover issues.
                return null; 
            }
            if (toEval.equals(out)) {
                return toEval;
            }
            toEval = out;
        }
    }
    
    // macro compile
    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, Long unspliceMask)
            throws Throwable {
        Class<?> returnType = type.returnType();
        if (returnType.equals(PersistentList.class)) {
            if (unspliceMask == 0) {
                MethodHandle handle = caller.findStatic(PersistentList.class, "reversed",
                        MethodType.methodType(PersistentList.class, Object[].class));
                handle = handle.asCollector(Object[].class, type.parameterCount());
                return new ConstantCallSite(handle.asType(type));
            } else {
                MethodHandle handle = caller.findStatic(PersistentList.class, "unspliceReverse",
                        MethodType.methodType(PersistentList.class, Long.TYPE, Object[].class));
                handle = MethodHandles.insertArguments(handle, 0, unspliceMask);
                handle = handle.asCollector(Object[].class, type.parameterCount());
                return new ConstantCallSite(handle.asType(type));
            }
        } else if (returnType.equals(PersistentVector.class)) {
            if (unspliceMask == 0) {
                return PersistentLiteralLinker.bootstrap(caller, "vec", type);
            } else {
                MethodHandle handle = caller.findStatic(PersistentVector.class, "unsplice",
                        MethodType.methodType(PersistentVector.class, Long.TYPE, Object[].class));
                handle = MethodHandles.insertArguments(handle, 0, unspliceMask);
                handle = handle.asCollector(Object[].class, type.parameterCount());
                return new ConstantCallSite(handle.asType(type));
            }
        } else {
            throw new PileInternalException("Unsplice not supported for type: " + returnType);
        }
    }

}
