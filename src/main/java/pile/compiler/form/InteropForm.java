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

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.InfiniteRecord;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.typed.Any;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.FunctionalInterfaceAdapter;
import pile.compiler.typed.TypedHelpers;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.IntrinsicBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.exception.UnlinkableMethodException;
import pile.core.indy.IndyHelpers;
import pile.core.indy.InteropInstanceMethodCallSite;
import pile.core.indy.InteropInstanceMethodCallSite.CrackError;
import pile.core.indy.InteropInstanceMethodCallSite.CrackResult;
import pile.core.indy.InteropInstanceMethodCallSite.ResultValue;
import pile.core.indy.InteropLinker;
import pile.core.indy.LinkOptions;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.core.runtime.generated_classes.LookupHolder;

public class InteropForm implements Form {

    private static final Logger LOG = LoggerSupplier.getLogger(InteropForm.class);

    enum InstanceOrStatic {
        INSTANCE, STATIC
    }

    enum FieldOrMethod {
        FIELD, METHOD
    }

    record InteropFormRecord(InstanceOrStatic ios, FieldOrMethod fom, Object instanceOrClass, String fieldOrMethodName,
            ISeq args) {
    }

    private record AdaptedArgs(int argCount, Optional<Method> maybeTarget) {
    }

    private static final Handle BOOTSTRAP_HANDLE = new Handle(Opcodes.H_INVOKESTATIC,
            Type.getType(InteropLinker.class).getInternalName(), "bootstrap",
            getBootstrapDescriptor(Type.getType(InteropType.class), LONG_TYPE, OBJECT_ARRAY_TYPE), false);

    private final PersistentList form;
    private final Namespace ns;

    private final boolean deferErrors;

    public InteropForm(PersistentList form) {
        this.form = form;
        this.ns = NativeDynamicBinding.NAMESPACE.getValue();
        this.deferErrors = NativeDynamicBinding.DEFER_ERRORS.getValue();
    }

    @Override
    public DeferredCompilation compileForm(CompilerState complierState) {

        InteropFormRecord formRecord = parseForm(complierState, ns, form);

        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.INTEROP, (CompilerState cs) -> {

            MethodVisitor mv = complierState.getCurrentMethodVisitor();
            MethodStack stack = complierState.getMethodStack();
            
            int beforeSize = stack.size();

            InstanceOrStatic instanceOrStatic = formRecord.ios;
            FieldOrMethod fieldOrMethod = formRecord.fom;
            Object instanceOrClass = formRecord.instanceOrClass;
            String fieldOrMethodName = formRecord.fieldOrMethodName;
            ISeq args = formRecord.args;
            try {
                switch (instanceOrStatic) {
                    case STATIC:
                        var classSym = expectSymbol(instanceOrClass);
                        Class<?> clazz = classSym.getAsClass(ns);
                        
                        switch (fieldOrMethod) {
                            case METHOD: {
                                compileStaticMethod(cs, mv, stack, clazz, fieldOrMethodName, args);
                                return;
                            }
                            case FIELD: {
                                compileStaticFieldGet(mv, stack, classSym, fieldOrMethodName);
                                return;
                            }
                        }
                    case INSTANCE:
                        switch (fieldOrMethod) {
                            case METHOD: {
                                compileInstanceMethod(cs, mv, stack, instanceOrClass, fieldOrMethodName, args);
                                return;
                            }
                            case FIELD: {
                                compileInstanceFieldGet(cs, mv, stack, instanceOrClass, fieldOrMethodName, args);
                                return;
                            }
                        }
                }
            
            } catch (PileException e) {
                // TODO Defer errors
                throw e;                
            }
        });

    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
    
    	InteropFormRecord formRecord = parseForm(cs, ns, form);
    
    	FieldOrMethod fieldOrMethod = formRecord.fom;
    	Object instanceOrClass = formRecord.instanceOrClass;
    	String fieldOrMethodName = formRecord.fieldOrMethodName;
    
        record BaseClass(Object base, Class<?> clazz) {};
    
        BaseClass bc = switch (formRecord.ios) {
            case STATIC:
                Symbol classSym = expectSymbol(instanceOrClass);
                Class<?> clazz = classSym.getAsClass(ns);
                yield new BaseClass(null, clazz);
            case INSTANCE:
                Object val = Compiler.evaluate(cs, instanceOrClass);
                ensureCompile(val != null, instanceOrClass, "Base val cannot be null");
                Class<?> iclazz = val.getClass();
                yield new BaseClass(val, iclazz);
        };
        List<Object> params = evaluateAdaptedArgs(cs, formRecord.args(), bc.clazz(), fieldOrMethodName, bc.base() == null);
        Class[] argClasses = Helpers.getArgClasses(params).toArray(Class[]::new);
        
        final var publicLookup = LookupHolder.PUBLIC_LOOKUP;
        
        switch (fieldOrMethod) {
            case METHOD: {
                DynamicTypeLookup<Method> lookup = new DynamicTypeLookup<Method>(TypedHelpers::ofMethod);
                Stream<Method> candidates = TypedHelpers.findMethods(bc.clazz(), fieldOrMethodName, bc.base() == null);
                Optional<Method> opt = lookup.findMatchingTarget(Arrays.asList(argClasses), Arrays.asList(argClasses), candidates);
                Method method = opt.orElseThrow(() -> makeError(form, formRecord, argClasses, bc.clazz(), bc.base()));

                CrackResult<MethodHandle> result = InteropInstanceMethodCallSite.crackReflectedMethod(publicLookup, bc.clazz(), fieldOrMethodName, method);

                MethodHandle handle = switch (result) {
                    case ResultValue(MethodHandle m) -> m;
                    case CrackError(String msg) -> throw new UnlinkableMethodException(msg); // TODO
                };

                var varArgs = handle.isVarargsCollector();
                if (formRecord.ios.equals(InstanceOrStatic.INSTANCE)) {
                    handle = handle.bindTo(bc.base());
                }
                handle = handle.withVarargs(varArgs);

                return handle.invokeWithArguments(params);
            }
            case FIELD: {
                Field f = bc.clazz().getField(fieldOrMethodName);
                return f.get(bc.base());
            }
            default:
                throw error("Unexpected enum:" + fieldOrMethod);
        }
    }

    private static InstanceOrStatic determineForm(CompilerState cs, Namespace ns, Object form) {

        TypeTag type = type(form);

        if (type == TypeTag.SYMBOL) {
            var classSym = expectSymbol(form);
            String classOrInstance = classSym.getName();
            Class<?> maybeClazz = classSym.tryGetAsClass(ns).orElse(null);

            if (maybeClazz != null) {
                return InstanceOrStatic.STATIC;
            }
            ScopeLookupResult slr = cs.getScope().lookupSymbolScope(new Symbol(classOrInstance));
            if (slr == null) {
                Optional<LexicalEnvironment> maybeLex = LexicalEnvironment.extract(classSym, form);
                throw new PileCompileException("Receiver symbol resolution failed.", maybeLex);
            }
        }
        return InstanceOrStatic.INSTANCE;
    }

    static InteropFormRecord parseForm(CompilerState complierState, Namespace ns, PersistentList form) {

        int size = form.count();

        ISeq seq = form.seq();
        ISeq secondOnArgs = seq.next();

        final InstanceOrStatic instanceOrStatic = determineForm(complierState, ns, second(form));

        final FieldOrMethod fieldOrMethod;
        final String fieldOrMethodName;
        final Object instanceOrClass = second(form);
        final ISeq args;

        // instance
        if (size == 3) {
            Object third = first(nnext(form));
            TypeTag type = type(third);
            if (type == TypeTag.SEXP) {
                // (. Classname-symbol (method-symbol args*))
                // (. instance-expr (method-symbol args*))

                fieldOrMethod = FieldOrMethod.METHOD;
                PersistentList methodSymbolAndArgs = expectList(third);

                fieldOrMethodName = strSym(first(methodSymbolAndArgs));
                args = more(methodSymbolAndArgs);

            } else if (type == TypeTag.SYMBOL) {
                args = ISeq.EMPTY;

                String symStr = strSym(third);
                if (symStr.startsWith("-")) {
                    // Fields
                    // (. Classname-symbol -member-symbol)
                    // (. instance-expr -field-symbol)
                    fieldOrMethodName = symStr.substring(1);
                    fieldOrMethod = FieldOrMethod.FIELD;
                } else {
                    // No arg methods
                    // (. Classname-symbol method-symbol)
                    // (. instance-expr method-symbol)
                    fieldOrMethodName = symStr;
                    fieldOrMethod = FieldOrMethod.METHOD;
                }
            } else {
                throw error("Bad interop form, expected SYMBOL or SEXP, found " + type);
            }
        } else if (size > 3) {
            // (. instance-expr method-symbol args*)
            fieldOrMethod = FieldOrMethod.METHOD;
            fieldOrMethodName = strSym(secondOnArgs.next().first());
            args = secondOnArgs.next().next();

        } else {
            throw error("Bad interop form");
        }

        return new InteropFormRecord(instanceOrStatic, fieldOrMethod, instanceOrClass, fieldOrMethodName, args);
    }

    private long getOptions() {
        long options = 0;
        if (NativeDynamicBinding.DEFER_ERRORS.getValue()) {
            options = LinkOptions.DEFER_ERRORS.set(options);
        }
        return options;
    }

    private void compileStaticFieldGet(MethodVisitor mv, MethodStack stack, Symbol classOrInstance, String symStr) {

        Class<?> clazz = classOrInstance.getAsClass(ns);
        try {
            if (clazz == null) {
                throw new PileCompileException("Unknown class symbol '" + classOrInstance + "' in " + ns.getName(),
                            LexicalEnvironment.extract(form));
            } else {
                Field f = clazz.getField(symStr);
                if (!Modifier.isStatic(f.getModifiers())) {
                    throw new PileCompileException("Cannot invoke static getter syntax on a non-static field: " + f,
                            LexicalEnvironment.extract(form));
                } else {
                    Class<?> returnType = f.getType();
                    String fieldDescriptor = Type.getDescriptor(returnType);
                    mv.visitFieldInsn(Opcodes.GETSTATIC, Type.getType(clazz).getInternalName(), symStr,
                            fieldDescriptor);
                    stack.push(returnType);
                }
            }
        } catch (SecurityException e) {
            throw new PileCompileException("Security exception trying to access field", LexicalEnvironment.extract(form));
        } catch (NoSuchFieldException e) {
            throw new PileCompileException("Unable to find field: " + symStr, LexicalEnvironment.extract(form));
        }

    }

    private void compileInstanceFieldGet(CompilerState cs, MethodVisitor mv, MethodStack stack, Object instanceForm,
            String fieldOrMethodName, ISeq args) {
        // Push instance
        Compiler.compile(cs, instanceForm);

        var receiver = stack.popR();

        // RETHINK
        // This will not run the compilation of the store if the expression never
        // returns.
        TypeRecord tr;
        switch (receiver) {
            case TypeRecord rec: tr = rec; break;
            case InfiniteRecord _: return;
        }
        Class<?> javaClass = tr.javaClass();
        Class<?> fieldType = Object.class;

        // TODO should this be Any?
        if (!(javaClass.equals(Object.class))) {
            try {
                Field declaredField = javaClass.getDeclaredField(fieldOrMethodName);
                fieldType = declaredField.getType();
                mv.visitFieldInsn(Opcodes.GETFIELD, Type.getType(javaClass).getInternalName(), fieldOrMethodName,
                        getDescriptor(fieldType));
                stack.push(fieldType);
                return;
            } catch (SecurityException e) {
                // pass
                // TODO Try to continue on to indy?
            } catch (NoSuchFieldException e) {                
                String msg = "Could not find field: " + javaClass + "." + fieldOrMethodName;                
                throw new PileCompileException(msg, LexicalEnvironment.extract(form));
            }
        }

        InteropType interopType = InteropType.INSTANCE_FIELD_GET;
        compileInteropIndy(mv, fieldOrMethodName, fieldType, List.of(tr), interopType);

        stack.pushAny();

    }

    private void compileStaticMethod(CompilerState cs, MethodVisitor mv, MethodStack stack, Class<?> clazz,
            String methodSymbol, ISeq args) {

        AdaptedArgs adaptedArgs = compileAdaptedArgs(cs, args, clazz, methodSymbol, true);
        Class<?> returnType = getReturnType(adaptedArgs);
        List<TypeRecord> typeRecords = stack.popN(adaptedArgs.argCount());

        long anyMask = getAnyMask(typeRecords);

        InteropType interopType = InteropType.STATIC_METHOD_CALL;

        mv.visitInvokeDynamicInsn(methodSymbol,
                Type.getMethodDescriptor(getType(toCompilableType(returnType)), Helpers.getJavaTypeArray(typeRecords)),
                BOOTSTRAP_HANDLE, IndyHelpers.forEnum(interopType), anyMask, clazz.getName());
        stack.push(returnType);

    }

    private void compileInstanceMethod(CompilerState cs, MethodVisitor mv, MethodStack stack, Object instanceForm,
            String methodSymbol, ISeq args) {
        // Push instance
        Compiler.compile(cs, instanceForm);
        Class<?> receiver = stack.peek();
        
        if (receiver.isPrimitive()) {
            String lex = LexicalEnvironment.extract(form).map(LexicalEnvironment::toString).orElse("Unknown line");
            LOG.warn("Compiling instance method against a promoted primitive %s @ %s", receiver, lex);
        }

        AdaptedArgs adaptedArgs = compileAdaptedArgs(cs, args, receiver, methodSymbol, false);
        Class<?> returnType = getReturnType(adaptedArgs);

        List<TypeRecord> typeRecords = stack.popN(adaptedArgs.argCount() + 1);

        InteropType interopType = InteropType.INSTANCE_CALL;
        compileInteropIndy(mv, methodSymbol, toCompilableType(returnType), typeRecords, interopType);

        stack.push(returnType);
    }

    private Class<?> getReturnType(AdaptedArgs adaptedArgs) {
        return adaptedArgs.maybeTarget().map(Method::getReturnType).orElse((Class) Any.class);
    }

    /**
     * Find an unambiguous single target method specified by the receiver type,
     * method name and arity.
     * 
     * @param receiver
     * @param arity
     * @param methodName
     * @return The single method.
     * @throws IllegalArgumentException If there is not a single method specified by
     *                                  the (type, methodName, arity).
     */
    private Method findUnambiguousTargetMethod(Class<?> receiver, int arity, String methodName) {
        List<Method> methods = Arrays.stream(receiver.getMethods())
                        .filter(m -> m.getName().equals(methodName))
                        .filter(m -> matchArity(m, arity))
                        .toList();
        if (methods.size() != 1) {
            throw new PileCompileException("Expected unambiguous target for " + receiver + "." + methodName
                    + " with " + arity + " args. Found " + methods.size() + " candidates");
        }
        return methods.get(0);
    }
    
    private static boolean matchArity(Method m, int arity) {
        final boolean isVarArg = m.isVarArgs();
        final int argSize = m.getParameterCount();
        if (isVarArg) {
            return argSize <= arity;
        } else {
            return argSize == arity;
        }
    }

    private AdaptedArgs compileAdaptedArgs(CompilerState cs, ISeq args, Class<?> receiver, String methodName,
            boolean isStatic) {

        int argCount = count(args);
        Method target = null;

        int stackCount = 0;
        for (var arg : ISeq.iter(args)) {
            if (FunctionalInterfaceAdapter.requiresAdapt(arg)) {
                if (target == null) {
                    target = findUnambiguousTargetMethod(receiver, argCount, methodName);
                }
                var targetInterface = target.getParameterTypes()[stackCount];
                FunctionalInterfaceAdapter adapter = new FunctionalInterfaceAdapter(cs, ns, targetInterface, arg);
                adapter.compile();
            } else {
                Compiler.compile(cs, arg);
            }
            ++stackCount;
        }
        return new AdaptedArgs(stackCount, Optional.ofNullable(target));
    }

    private List<Object> evaluateAdaptedArgs(CompilerState cs, ISeq args, Class<?> receiver, String methodName,
            boolean isStatic) throws Throwable {

        int argCount = count(args);
        Method target = null;

        List<Object> evaluatedArgs = new ArrayList<>();

        int stackCount = 0;
        for (var arg : ISeq.iter(args)) {
            if (FunctionalInterfaceAdapter.requiresAdapt(arg)) {
                if (target == null) {
                    target = findUnambiguousTargetMethod(receiver, argCount, methodName);
                }
                var targetInterface = target.getParameterTypes()[stackCount];
                FunctionalInterfaceAdapter adapter = new FunctionalInterfaceAdapter(cs, ns, targetInterface, arg);
                evaluatedArgs.add(adapter.evaluate());
            } else {
                evaluatedArgs.add(Compiler.evaluate(cs, arg));
            }
            ++stackCount;
        }
        return evaluatedArgs;
    }

    private void compileInteropIndy(MethodVisitor mv, String methodSymbol, Class<?> pileReturnType,
            List<TypeRecord> typeRecords, InteropType interopType) {

        var javaArgs = Helpers.getJavaTypeArray(typeRecords);
        long anyMask = getAnyMask(typeRecords);

        var returnType = toCompilableType(pileReturnType);

        mv.visitInvokeDynamicInsn(methodSymbol, Type.getMethodDescriptor(getType(returnType), javaArgs),
                BOOTSTRAP_HANDLE, IndyHelpers.forEnum(interopType), anyMask);
    }

    private static PileSyntaxErrorException makeError(PersistentList form, InteropFormRecord formRecord,
            Class[] argClasses, Class<?> clazz, Object base) {
        Optional<LexicalEnvironment> maybeLex = LexicalEnvironment.extract(form);
        String argStr = Arrays.stream(argClasses).map(Class::toString).collect(Collectors.joining(", "));
        String msg = String.format("Unable to find %S method %s/%s(%s) to call", formRecord.ios(), clazz.getName(),
                formRecord.fieldOrMethodName(), argStr);
        return new PileSyntaxErrorException(msg, maybeLex);
    }
    
    public static String DOCUMENTATION = """
            Generic interop function.
            
            ;; Static Method Call
            ;; (. class-symbol (method arg_0 arg_1 .. arg_N))
            ;; (. class-symbol method arg_0 arg_1 .. arg_N)
            ;; (class-symbol/method arg_0 arg_1 .. arg_N)
            (. String (format "first: %s second %s" "one" "two"))
            (String/format "first: %s second %s" "one" "two")
            
            ;; Instance Method Call
            ;; (. base (method arg_0 arg_1 .. arg_N))
            ;; (.method base arg_0 arg_1 .. arg_N)
            (. "abcd" (length))
            (.length "abcd")
            
            ;; Static Field Get
            ;; (. class-symbol -member-symbol)
            ;; (class-symbol/-member-symbol)
            (. Integer -BYTES)
            (Integer/-BYTES)
            
            ;; Instance Field Get
            ;; (. base -member-symbol)
            (def coord (pile.util.Coordinate.))
            (. coord -x)
            
            """;
}
