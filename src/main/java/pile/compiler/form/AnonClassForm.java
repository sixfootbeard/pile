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
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.ClassCompiler;
import pile.compiler.ClassCompiler.CompiledMethodResult;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MethodDefiner;
import pile.compiler.MethodDefiner.MethodRecord;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.ParameterParser;
import pile.compiler.typed.Any;
import pile.compiler.typed.StaticTypeLookup;
import pile.compiler.typed.TypedHelpers;
import pile.core.CoreConstants;
import pile.core.Symbol;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.ShouldntHappenException;
import pile.core.exception.UnlinkableMethodException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.util.InvokeDynamicBootstrap;

public class AnonClassForm extends AbstractListForm {

    private record SuperType(Class<?> superType, PersistentVector<Object> superConstructorArgs) {
    }

    private record DefinedAnonClass(Class<?> definedType, PersistentVector<Object> constructorArgs,
            PersistentVector<Object> clojureArgs, int closureStart, List<Object> evaluatedArgs,
            boolean isVarArgs) {
    }

    public AnonClassForm(PersistentList form) {
        super(form);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {

        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.ANON_CLASS, cs -> {
            DefinedAnonClass anon;
            try {
                anon = compileClass(cs, true);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new PileCompileException("Should only happen during eval.", LexicalEnvironment.extract(form), e);
            }
            
            // constructor args
            List<TypeRecord> constructorArgs = Compiler.compileArgs(cs, anon.constructorArgs().seq());
            // closure args
            PersistentVector<Object> closureArgs = anon.clojureArgs();
            List<TypeRecord> closureRecords = Compiler.compileArgs(cs, closureArgs.seq());
            
            MethodStack methodStack = cs.getMethodStack();
            List<TypeRecord> typeRecord = new ArrayList<>();
            typeRecord.addAll(constructorArgs);
            typeRecord.addAll(closureRecords);
            Class<?> compiledClassName = anon.definedType();
            Type[] typeArray = getJavaTypeArray(typeRecord);
            indy(cs.getCurrentMethodVisitor(), "init", AnonClassForm.class, compiledClassName, typeArray,
                    anon.closureStart(), anon.isVarArgs() ? 1 : 0);
            methodStack.push(compiledClassName);
        });
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        DefinedAnonClass anon = compileClass(cs, false);
        PersistentVector<Object> closureArgs = anon.clojureArgs();
        List<Object> closureEvaluatedArgs = Compiler.evaluateArgs(cs, closureArgs.seq());

        List<Object> evaluated = anon.evaluatedArgs();
        evaluated.addAll(closureEvaluatedArgs);

        List<Class<?>> classes = getArgClasses(evaluated);
        MethodType type = methodType(anon.definedType(), classes);

        CallSite callSite = bootstrap(lookup(), "method", type, anon.closureStart(), anon.isVarArgs() ? 1 : 0);
        return callSite.dynamicInvoker().invokeWithArguments(evaluated);
    }

    private DefinedAnonClass compileClass(CompilerState cs, boolean compile) throws Throwable {

        // @formatter:off
        //
        // (anon-cls 
        //     ExtendsClass [super class args]
        //     (method [this arg])
        //     Interface
        //     (another-method [this arg]) )
        //
        // @formatter:on

        String typeName = "anoncls$" + ns.getSuffix();

        // collect classes and args
        PersistentList<Object> classesAndArgs = form.pop();

        SuperType superType = null;
        List<Class<?>> interfaces = new ArrayList<>();
        List<MethodRecord> methods = new ArrayList<>();

        Iterator<Object> it = classesAndArgs.iterator();

        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof Symbol sym) {
                var asClass = sym.getAsClass(ns);

                if (!asClass.isInterface()) {
                    if ((asClass.getModifiers() & Modifier.FINAL) != 0) {
                        var pos = LexicalEnvironment.extract(sym);
                        throw new PileCompileException("Super type may not be final.", pos);
                    }
                    if (superType != null) {
                        throw new PileCompileException("Cannot have more than one non-interface supertype", LexicalEnvironment.extract(o, form));
                    }
                    Class<?> superClass = asClass;
                    if (! it.hasNext()) {
                        var pos = LexicalEnvironment.extract(form);
                        throw new PileCompileException(
                                "Supertype must be followed by a vector of args to call a super constructor", pos);
                    }
                    Object superConstructorArgs = it.next();
                    if (! (superConstructorArgs instanceof PersistentVector)) {
                        // TODO use type tag?
                        var pos = LexicalEnvironment.extract(superConstructorArgs, form);
                        throw new PileCompileException(
                                "Supertype must be followed by a vector of args to call a super constructor", pos);
                    }
                    superType = new SuperType(superClass, expectVector(superConstructorArgs));
                } else {
                    interfaces.add(asClass);
                }
            } else if (o instanceof PersistentList pl) {
                methods.addAll(MethodDefiner.parseNamed(ns, pl));
            } else {
                Optional<LexicalEnvironment> maybeLex = LexicalEnvironment.extract(o, form);
                throw new PileCompileException(
                        "Bad anonymous class part, expected symbol or list, found: " + o.getClass(), maybeLex);

            }
        }
        
        var compiledSuper = superType == null ? Object.class : superType.superType();

        ClassCompiler method = new ClassCompiler(ns, typeName, CoreConstants.GEN_PACKAGE);
        try (var ignored = method.enterClass(cs, compiledSuper, interfaces)) {
            method.createAnonymousClass(cs);

            new MethodDefiner().defineMethods(ns, cs, method, compiledSuper, interfaces, methods);

            // Define all supers and interfaces

            PersistentVector<Object> constructorArgs = PersistentVector.EMPTY;
            PersistentVector<Object> closureArgs = PersistentVector.EMPTY;

            List<Object> evaluatedArgs = new ArrayList<>();
            boolean isVarArgs = false;

            if (superType != null) {
                // has super
                constructorArgs = superType.superConstructorArgs();
                // ~~ Merge constructor + closure args
                // find constructor implied by vector args
                // append closure args

                List<Class<?>> consTypes;
                if (compile) {
                    var typeRecords = Compiler.compileArgs(cs, superType.superConstructorArgs().seq());
                    consTypes = mapL(typeRecords, TypeRecord::clazz); 
                } else {
                    evaluatedArgs = Compiler.evaluateArgs(cs, superType.superConstructorArgs().seq());
                    consTypes = getArgClasses(evaluatedArgs);
                }

                StaticTypeLookup<Constructor<?>> lookup = new StaticTypeLookup<>(TypedHelpers::ofConstructor);
                Stream<Constructor<?>> consCandidates = Arrays.stream(superType.superType().getConstructors());

                var parentClass = superType.superType();
                Constructor<?> cons = lookup.findSingularMatchingTarget(consTypes, consCandidates)
                                            .orElseThrow(() -> unlinkableThrow(consTypes, parentClass));

                isVarArgs = cons.isVarArgs();
                method.setTargetSuperConstructor(ParameterParser.from(cons).withJavaVarArgs(false));
            }

            int closureStart = constructorArgs.count();

            List<Symbol> closureSyms = cs.getClosureSymbols().values().stream()
                    .map(cr -> new Symbol(cr.symbolName()).withTypeAnnotation(cr.type())).toList();

            closureArgs = closureArgs.pushAll(closureSyms);

            method.exitClass(cs);
            CompiledMethodResult cmr = method.wrap(cs);
            return new DefinedAnonClass(cmr.clazz(), constructorArgs, closureArgs, closureStart, evaluatedArgs, isVarArgs);
        }

    }
    
    private UnlinkableMethodException unlinkableThrow(List<Class<?>> types, Class<?> clazz) {
        String raw = "Could not find unambiguous constructor to call in %s with types: %s. Consider using type hints.";
        String msg = String.format(raw, clazz, mapL(types, t -> Any.class.equals(t) ? Object.class : t));
        return new UnlinkableMethodException(msg);
    }

    /**
     * 
     * @param caller
     * @param method
     * @param type
     * @param closureStart Position in the type where the closure arguments start
     * @param isVarArgs    The constructor we're calling cannot be varargs if there
     *                     are closure arguments (internal java invariant dies not
     *                     during construction of the class but in realizing the
     *                     constructor as a method handle within
     *                     {@link Lookup#unreflectConstructor(Constructor)}) , so we
     *                     pass this separately.
     * @return
     * @throws Throwable
     */
    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, Integer closureStart,
            Integer isVarArgs) throws Throwable {

        var clazz = type.returnType();
        final Constructor<?> cons = clazz.getConstructors()[0];
        final MethodHandle handle = caller.unreflectConstructor(cons);

        if (isVarArgs == 0) {
            return new ConstantCallSite(handle.asType(type));
        } else {
            //@formatter:off
            // Arguments are pushed on without regard to varargs, but the constructor we're
            // targeting may be varargs.
            
            // Pushed args:       A B C C C CL1 CL2
            // Our Constructor:   A B C[] CL1 CL2
            // Super Constructor: A B C...
            
            // Pushed args could be less (no C):
            // Pushed Args:       A B     CL1 CL2
            // Our Constructor:   A B C[] CL1 CL2
            
            // Equal size with varargs still needs some work:
            // Pushed Args:       A B C   CL1 CL2
            // Should be:         A B C[] CL1 CL2
            //@formatter:on
            
            int constructorVarArgsPosition = handle.type().parameterCount() - (type.parameterCount() - closureStart) - 1;
            Class<?> parameterType = handle.type().parameterType(constructorVarArgsPosition);

            final int diff = type.parameterCount() - handle.type().parameterCount();
            int varArgsStart = constructorVarArgsPosition;
            if (diff == -1) {
                Class<?> componentType = parameterType.componentType();
                MethodHandle arrayConstructor = arrayConstructor(componentType.arrayType());
                var emptyTypedArray = arrayConstructor.invoke(0);
                MethodHandle withEmptyArray = insertArguments(handle, varArgsStart, emptyTypedArray);
                return new ConstantCallSite(withEmptyArray.asType(type));
            } else if (diff > 0) {
                MethodHandle collected = handle.asCollector(varArgsStart, parameterType, diff + 1);
                return new ConstantCallSite(collected.asType(type));
            } else if (diff == 0) {
                MethodHandle collected = handle.asCollector(varArgsStart, parameterType, 1);
                return new ConstantCallSite(collected.asType(type));
            } else {
                throw new ShouldntHappenException("Unhandled case or fell through.");
            }
        }
    }

}
