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
package pile.compiler.typed;

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.compiler.AbstractClassCompiler.CompiledMethodResult;
import pile.compiler.ClosureClassCompiler;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DefTypeClassCompiler;
import pile.compiler.MethodDefiner;
import pile.compiler.MethodDefiner.MethodRecord;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.ParameterParser;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.form.AnonFnForm;
import pile.compiler.form.SExpr;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.PileParser;
import pile.core.parse.TypeTag;

/**
 * Adapts a form ({@link Symbol} or {@link SExpr}) referring to a bound function
 * or anonymous function and creates an adapter capable of transforming that
 * method into the target functional interface type. Supports both compilation
 * and evaluation.
 *
 */
public class FunctionalInterfaceAdapter {

    private static final Symbol ANON_FN_SYM = new Symbol("pile.core", "anon-fn");

    private static final Symbol APPLY_SYM = new Symbol("pile.core", "apply");

    private final Namespace ns;
    private final Object form;
    private final CompilerState cs;
    private final Class<?> targetInterface;

    public FunctionalInterfaceAdapter(CompilerState cs, Namespace ns, Class<?> targetInterface, Object form) {
        super();
        this.cs = cs;
        this.ns = ns;
        this.form = form;
        this.targetInterface = targetInterface;
    }

    public void compile() {
        Method methodToImplement = findMethodToImplement(targetInterface);
        TypeTag tag = getTag(form);
        switch (tag) {
            case SEXP -> compileAdaptedAnonFn(cs, methodToImplement, (PersistentList) form);
            case SYMBOL -> compileAdaptedSymbol(cs, methodToImplement, (Symbol) form);
            default -> throw new PileCompileException("Bad adapt syntax type: " + tag, LexicalEnvironment.extract(form));
        }
    }

    public Object evaluate() throws Throwable {
        Method methodToImplement = findMethodToImplement(targetInterface);
        TypeTag tag = getTag(form);
        return switch (tag) {
            case SEXP -> evaluatedAdaptedAnonFn(cs, methodToImplement, (PersistentList) form);
            case SYMBOL -> evaluateAdaptedSymbol(cs, methodToImplement, (Symbol) form);
            default -> throw new PileCompileException("Bad adapt syntax type: " + tag, LexicalEnvironment.extract(form));
        };
    }

    /**
     * Find the method handle of the single method in the supplied functional
     * interface.
     * 
     * @param functionalInterface
     * @return
     */
    public static Method findMethodToImplement(Class<?> functionalInterface) {
        return findMethodToImplementOpt(functionalInterface).orElseThrow(
                () -> new PileCompileException("Expected functional interface, found: " + functionalInterface));
    }

    public static Optional<Method> findMethodToImplementOpt(Class<?> functionalInterface) {
        //@formatter:off
        List<Method> candidates = Arrays.stream(functionalInterface.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .filter(m -> ! Modifier.isStatic(m.getModifiers()))
                .filter(m -> ! m.isDefault())
                .toList();
        //@formatter:on

        if (candidates.size() != 1) {
            // Could be a filter, but the check is slow and uncommon
            // Classes like Comparator define equals which overrides
            // Object.equals but isn't considered when checking FI
            candidates = new ArrayList<>(candidates);
            candidates.removeIf(FunctionalInterfaceAdapter::isObjectMethod);
        }

        if (candidates.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }
    
    private static boolean isObjectMethod(Method m) {
        try {
            Object.class.getMethod(m.getName(), m.getParameterTypes());
            return true;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    private void compileAdaptedSymbol(CompilerState cs, Method m, Symbol sym) {
        MethodStack stack = cs.getMethodStack();

        Class<?> compiledClass = createSymbolClass(cs, m, sym);

        MethodVisitor mv = cs.getCurrentMethodVisitor();
        String internalName = getType(compiledClass).getInternalName();
        mv.visitTypeInsn(Opcodes.NEW, internalName);
        mv.visitInsn(Opcodes.DUP);
        // Push single cons arg
        Compiler.compile(cs, sym);
        stack.pop();
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName, "<init>", getMethodDescriptor(VOID_TYPE, OBJECT_TYPE),
                false);

        stack.push(compiledClass);
    }

    private void compileAdaptedAnonFn(CompilerState cs, Method m, PersistentList arg) {
        var cmr = createAnonFnClass(cs, m, list(ANON_FN_SYM, arg));

        MethodVisitor mv = cs.getCurrentMethodVisitor();
        MethodStack stack = cs.getMethodStack();

        String internalName = getType(cmr.clazz()).getInternalName();
        mv.visitTypeInsn(Opcodes.NEW, internalName);
        mv.visitInsn(Opcodes.DUP);

        // closure args
        int count = 0;
        for (String sym : cmr.closureSymbols().keySet()) {
            Compiler.compile(cs, new Symbol(sym));
            ++count;
        }
        List<TypeRecord> popN = cs.getMethodStack().popN(count);
        Type[] typeArray = getJavaTypeArray(popN);
        String methodDescriptor = getMethodDescriptor(VOID_TYPE, typeArray);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName, "<init>", methodDescriptor, false);

        stack.push(cmr.clazz());

    }

    private Object evaluateAdaptedSymbol(CompilerState cs, Method methodToImplement, Symbol adaptedForm)
            throws Throwable {
        Class<?> clazz = createSymbolClass(cs, methodToImplement, adaptedForm);
        var cons = clazz.getDeclaredConstructor(Object.class);
        var symEval = Compiler.evaluate(cs, adaptedForm);
        return cons.newInstance(symEval);
    }

    private Object evaluatedAdaptedAnonFn(CompilerState cs, Method methodToImplement, PersistentList adaptedForm)
            throws Throwable {
        var cmr = createAnonFnClass(cs, methodToImplement, list(ANON_FN_SYM, adaptedForm));
        ensure(cmr.closureSymbols().isEmpty(), "Cannot evaluate form with closure symbols");
        return cmr.clazz().getDeclaredConstructor().newInstance();
    }

    /**
     * This should have the same effect as passing a function around within any
     * normal method. That is, redefs to the symbol being passed as a function
     * should have no effect to extant instances. Here we create a synthetic closure
     * which captures a reference to the current value of the function being called.
     * 
     * @param cs
     * @param m
     * @param sym
     * @return
     */
    private Class<?> createSymbolClass(CompilerState cs, Method m, Symbol sym) {

        Class<?>[] parameterTypes = m.getParameterTypes();
        ParameterList pr = ParameterParser.noName(Arrays.asList(parameterTypes));

        var parent = targetInterface;

        final Class<?> superType;
        final List<Class<?>> interfaces;
        if (parent.isInterface()) {
            superType = Object.class;
            interfaces = List.of(parent);
        } else {
            superType = parent;
            interfaces = List.of();
        }

        var closureSym = gensym();

        // (closureSym# arg0 arg1 ... argN)
        // (apply closureSym# arg0 arg1 ... argN)
        List<Object> fcall = new ArrayList<>();
        if (m.isVarArgs()) {
            pr = pr.withVarArgs();
            fcall.add(APPLY_SYM);
        }
        fcall.add(closureSym);
        pr.args().forEach(ar -> fcall.add(sym(ar.name())));

        // OPTIMIZE Use sym type?
        var consArgs = ParameterList.empty().append(new MethodParameter(closureSym.getName(), ANY_CLASS));

        // Compile
        var cc = new DefTypeClassCompiler(ns);
        try (var ignored = cc.enterClass(cs, superType, interfaces)) {
            cc.setFieldList(consArgs);
            cc.defineConstructor(cs);

            // FIXME This double wrapping is kinda silly
            PersistentList<Object> body = PersistentList.createArr(PersistentList.fromList(fcall));

            var withThis = pr.prepend(new MethodParameter(gensym().getName(), ANY_CLASS));

            MethodDefiner def = new MethodDefiner();
            MethodRecord mrec = new MethodRecord(m.getName(), withThis, body);
            def.defineMethods(ns, cs, cc, superType, interfaces, List.of(mrec));

            cc.exitClass(cs);
        } catch (IllegalAccessException e) {
            throw new PileInternalException(e);
        }

        Class<?> compiledClass = cc.getCompiledClass();
        return compiledClass;
    }

    private CompiledMethodResult createAnonFnClass(CompilerState cs, Method m, PersistentList anonFn) {

        // (fn* [args] & form)
        PersistentList expandedForm = AnonFnForm.generateExpandedForm(anonFn);

        var expandedArgList = expectVector(second(expandedForm));

        // Merge anonFn implied arg list syms with method parameter size (potentially
        // lengthening the implied arg list size) along with pulling the types.
        Class<?>[] parameterTypes = m.getParameterTypes();
        ParameterList pr = ParameterParser.noName(Arrays.asList(parameterTypes));

        for (int i = expandedArgList.size(); i < parameterTypes.length; ++i) {
            expandedArgList = expandedArgList.conj(gensym());
        }

        ParameterList argPr = new ParameterParser(ns, expandedArgList).parse();

        argPr = argPr.updateTypes(m);
        if (m.isVarArgs()) {
            argPr = argPr.withJavaVarArgs();
        }

        var parent = targetInterface;

        final Class<?> superType;
        final List<Class<?>> interfaces;
        if (parent.isInterface()) {
            superType = Object.class;
            interfaces = List.of(parent);
        } else {
            superType = parent;
            interfaces = List.of();
        }

        // TODO Wrap?
        PersistentList fcall = expandedForm.pop().pop();

        // Compile
        var cc = new ClosureClassCompiler(ns);
        try (var ignored = cc.enterClass(cs, superType, interfaces)) {
            
            var withThis = argPr.prepend(new MethodParameter(gensym().getName(), ANY_CLASS));

            MethodDefiner def = new MethodDefiner();
            MethodRecord mrec = new MethodRecord(m.getName(), withThis, fcall);
            def.defineMethods(ns, cs, cc, superType, interfaces, List.of(mrec));

            cc.defineConstructor(cs);
            cc.exitClass(cs);
            // Push class we just compiled, which is-a <functionalInterface> type
            return cc.wrap(cs);
        } catch (IllegalAccessException e) {
            throw new PileInternalException(e);
        }

    }

    public static boolean requiresAdapt(Object arg) {
        if (arg instanceof Metadata metaObj) {
            var meta = metaObj.meta();
            boolean containsKey = meta.containsKey(PileParser.ADAPT_TYPE);
            if (containsKey) {
                return (boolean) meta.get(PileParser.ADAPT_TYPE);
            }
        }
        return false;
    }

}