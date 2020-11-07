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
package pile.compiler;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;
import static pile.util.CollectionUtils.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import pile.collection.PersistentList;
import pile.compiler.CompilerState.ClosureRecord;
import pile.compiler.MethodCollector.MethodArity;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.annotation.GeneratedMethod;
import pile.compiler.annotation.PileVarArgs;
import pile.compiler.form.VarScope;
import pile.core.CoreConstants;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.compiler.aot.AOTHandler;
import pile.core.compiler.aot.AOTHandler.AOTType;
import pile.core.exception.PileInternalException;
import pile.core.log.LogLevel;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.runtime.generated_classes.LookupHolder;

/**
 * 
 * Compiles method representations into bytecode. <br>
 * <br>
 * Defining a type (deftype):
 * <ol>
 * <li>enterClass
 * <li>{@link #createExplicitConstructorWithFields(CompilerState, ParameterList)}
 * <li>createSingleMethod
 * <li>{@link #exitClass(CompilerState)}
 * </ol>
 * Defining a closure (all closed over symbols are captured):
 * <ol>
 * <li>enterClass
 * <li>{@link #createClosure()}
 * <li>createSingleMethod*
 * <li>{@link #exitClass(CompilerState)}
 * </ol>
 * Defining an anonymous class (explicit constructor + closure args)
 * <ol>
 * <li>enterClass
 * <li>{@link #createAnonymousClass(CompilerState)}
 * <li>{@link #setTargetSuperConstructor(ParameterList)}
 * <li>createSingleMethod*
 * <li>{@link #exitClass(CompilerState)}
 * </ol>
 * 
 * After:
 * <ul>
 * <li>{@link #getCompiledClass()}
 * <li>{@link #wrap(CompilerState)} ?
 * </ul>
 *
 * @see SingleMethodCompilerBuilder
 */
public class ClassCompiler {

    public static final Keyword RETURN_TYPE_KEY = Keyword.of(null, "return-type");

    private static final Lookup MC_LOOKUP = lookup();
    private static final Logger LOG = LoggerSupplier.getLogger(ClassCompiler.class);

    static enum MethodCompilerType { CLOSURE, DEF_TYPE, ANON_CLASS; }

    public record CompiledMethodResult(Class<?> clazz, Map<String, ClosureRecord> closureSymbols) {
    }

    private static final String NAMESPACE_GET_METHOD = Type.getMethodDescriptor(Type.getType(Binding.class),
            Type.getType(Namespace.class), Type.getType(String.class));
    private static final String DEREF_METHOD = Type
            .getMethodDescriptor(Type.getType(Object.class)/* , Type.getType(Deref.class) */);

    private static final AtomicInteger formSuffix = new AtomicInteger();

    private static final Symbol DO_SYM = new Symbol("pile.core", "do");
    
    private final Namespace ns;
    private final String className;
    private final String packageName;
    private List<Class<?>> interfaces;
    
    // anon class
    private ParameterList constructorArgs = null;
    private Class<?> superType = null;

    private Class<?> clazz;
    private boolean hasFieldScope = false;

    private MethodCompilerType methodType;

    public ClassCompiler(Namespace ns) {
        this(ns, "fclass$" + ns.getSuffix(), CoreConstants.GEN_PACKAGE);
    }

    public ClassCompiler(Namespace ns, String className, String internalName) {
        this.ns = NativeDynamicBinding.NAMESPACE.getValue();
        this.className = className;
        this.packageName = internalName;
    }

    public CloseNoThrow enterClass(CompilerState cs) {
        return enterClass(cs, Object.class);
    }

    public CloseNoThrow enterClass(CompilerState cs, Class<?> superType) {
        return enterClass(cs, superType, List.of());
    }
    
    public CloseNoThrow enterClass(CompilerState cs, Class<?> superType, List<Class<?>> interfaces) {
        cs.enterClass(getInternalName(), superType, interfaces);
        this.superType = superType;
        this.interfaces = interfaces;
        return () -> cs.leaveClass();
    }
    
    public void createClosure() {
        setMethodType(MethodCompilerType.CLOSURE);
    }

    /**
     * Create a constructor with the provided fields. This is optional, and cannot
     * be called if creating a closure.
     * 
     * @param cs
     * @param pr
     */
    public void createExplicitConstructorWithFields(CompilerState cs, ParameterList pr) {
        setMethodType(MethodCompilerType.DEF_TYPE);
        // Fields
        createFields(cs, pr);

        // Constructor
        defineConstructor(cs, pr);
        
    }
    
    public void createAnonymousClass(CompilerState cs) {
        setMethodType(MethodCompilerType.ANON_CLASS);
        
        // ??
    }

    public void setTargetSuperConstructor(ParameterList cons) {
        this.constructorArgs = cons;
    }

    /**
     * Create a method:
     * <ol>
     * <li>Return Type - [returnType]
     * <li>Name - func$[random-suffix]
     * <li>Form - (args body)
     * </ol>
     * 
     * @param cs
     * @param seq
     */
    public void createSingleMethod(CompilerState cs, Class<?> returnType, PersistentList seq) {
        String methodName = "func$" + formSuffix.incrementAndGet();
        createSingleMethod(cs, methodName, returnType, seq);
    }

    /**
     * Create a method:
     * <ol>
     * <li>Return Type - [returnType]
     * <li>Name - [methodName]
     * <li>Form - (args body)
     * </ol>
     * 
     * @param cs
     * @param methodName
     * @param returnType
     * @param seq
     */
    public void createSingleMethod(CompilerState cs, String methodName, Class<?> returnType, PersistentList vecAndBody) {
        var vars = expectVector(first(vecAndBody));

        ParameterParser argParser = new ParameterParser(ns, vars);
        ParameterList parseRecord = argParser.parse();
        
        // Vector hint overrides any supplied hint.
        Class<?> scopedHint = Helpers.getTypeHint(vars, ns).orElse(returnType);

        List annos = new ArrayList<>();
        annos.add(GeneratedMethod.class);
        if (parseRecord.isVarArgs()) {
            annos.add(PileVarArgs.class);
        }

        createSingleMethod(cs, methodName, scopedHint, parseRecord, annos, vecAndBody.pop());
    }

    /**
     * Create a method:
     * <ol>
     * <li>Return Type - [returnType]
     * <li>Name - [methodName]
     * <li>Form - (body)
     * </ol>
     * 
     * @param cs
     * @param methodName  The name of the created method.
     * @param returnType  The method signature return type
     * @param parseRecord The pr which defines the method parameter names and types.
     * @param annos       Any annotations on the method
     * @param body        The body of the function.
     */
    public void createSingleMethod(CompilerState cs, String methodName, Class<?> returnType, ParameterList parseRecord,
            List<Class<Annotation>> annos, PersistentList body) {
        createSingleMethod(cs, methodName, returnType, parseRecord, annos, ACC_PUBLIC, body);
    }
    
    public void createSingleMethod(CompilerState cs, String methodName, Class<?> returnType, ParameterList parseRecord,
            List<Class<Annotation>> annos, int flags, PersistentList body) {
            
        SingleMethodCompilerBuilder builder = new SingleMethodCompilerBuilder(cs);
        
        builder.withMethodName(methodName).withReturnType(returnType).withParseRecord(parseRecord)
                .withAnnotations(annos).withMethodFlags(flags).withBody(body).build();

    }
    
    /**
     * Create a method with a custom implementation of the method body bytecode
     * generator (instead of evaluating source code data structures).
     * 
     * @param cs
     * @param methodName
     * @param returnType
     * @param parseRecord
     * @param annos
     * @param methodFlags Method flags (eg {@link Opcodes#ACC_PUBLIC})
     * @param cons
     */
    public void createSingleMethodCustom(CompilerState cs, String methodName, Class<?> returnType, ParameterList parseRecord,
            List<Class<Annotation>> annos, int methodFlags, Consumer<MethodVisitor> cons) {
        SingleMethodCompilerBuilder builder = new SingleMethodCompilerBuilder(cs);
        
        builder.withMethodName(methodName).withReturnType(returnType).withParseRecord(parseRecord)
                .withAnnotations(annos).withMethodFlags(methodFlags).withBody(cons).build();
    }
    
    

//    public void exitMethod(CompilerState cs) {
//        try {
//            MethodVisitor method = cs.getCurrentMethodVisitor();
//            method.visitMaxs(0, 0);
//            method.visitEnd();
//        } catch (Throwable t) {
//            t.printStackTrace();
//            throw t;
//        } finally {
//            cs.leaveMethod();
//            cs.getScope().leaveScope();
//        }
//    }

    /**
     * Complete defining this class and compile it.
     * 
     * @param cs
     * @throws IllegalAccessException
     */
    public void exitClass(CompilerState cs) throws IllegalAccessException {

        ClassVisitor writer = cs.getCurrentVisitor();
        switch (methodType) {
            case DEF_TYPE:
                ensure(cs.getClosureSymbols().isEmpty(),
                        "Cannot define a method with an explicit constructor and closed over variables");
                break;
            case CLOSURE:
                defineClosureConstructor(cs, writer);
                break;
            case ANON_CLASS:
                defineAnonConstructor(cs, writer);
                break;

        }

        // cleanup
        writer.visitEnd();
        createClass(cs);
        if (hasFieldScope) {
            // VarScope.FIELD
            cs.getScope().leaveScope();
        }
    }
    
    public String getInternalName() {
        return packageName + "/" + className;
    }

    /**
     * Get the compiled class. Only call after {@link #exitClass(CompilerState)}.
     * 
     * @return
     */
    public Class<?> getCompiledClass() {
        if (clazz == null) {
            throw new PileInternalException("Called in the wrong order");
        }
        return clazz;
    }
    
    public CompiledMethodResult wrap(CompilerState cs) {
        Map<String, ClosureRecord> closureSymbols = cs.getClosureSymbols();
        return new CompiledMethodResult(getCompiledClass(), closureSymbols);
    }

   
    private void createFields(CompilerState cs, ParameterList pr) {
        ensure(!hasFieldScope, "Cannot create multiple fields");
        ClassVisitor visitor = cs.getCurrentVisitor();
        Scopes scope = cs.getScope();
        scope.enterScope(VarScope.FIELD);
    
        for (MethodParameter ar : pr.args()) {
            // Create field
            String fieldName = ar.name();
            FieldVisitor field = visitor.visitField(ACC_FINAL, fieldName, ar.getCompilableTypeDescriptor(), 
                    null, null);
            field.visitEnd();
    
            scope.addCurrent(fieldName, ar.type());
        }
    
        this.hasFieldScope = true;
    }

    private void setMethodType(MethodCompilerType type) {
        ensure(methodType == null, "Cannot set method type more than once");
        this.methodType = type;        
    }

    private void createClass(CompilerState cs) throws IllegalAccessException {
        byte[] classArray = cs.compileClass();

        printDebug(classArray);
        
        if (AOTHandler.getAotType() == AOTType.WRITE) {
            AOTHandler.writeAOTClass(packageName, className, classArray);
        }

        clazz = LookupHolder.LOOKUP.defineClass(classArray);
    }

    private void defineAnonConstructor(CompilerState cs, ClassVisitor writer) {

        Map<String, ClosureRecord> closureSymbols = cs.getClosureSymbols();

        // append (called constructor args) + (closure args)
        List<MethodParameter> closureArgs = toArgRecord(closureSymbols);

        final ParameterList base;
        if (constructorArgs == null) {
            base = ParameterList.empty();
        } else {
            base = constructorArgs;
        }

        ParameterList withClosureArgs = base.append(closureArgs);
        int flags = ACC_PUBLIC;
        if (withClosureArgs.isJavaVarArgs()) {
            flags |= ACC_VARARGS;
        }
        MethodVisitor cons = cs.enterMethod("<init>", void.class, flags, withClosureArgs);
        try {
            GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();

            cons.visitCode();
            cons.visitVarInsn(ALOAD, 0);
            if (constructorArgs == null) {
                cons.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            } else {
                for (int i = 0; i < base.args().size(); ++i) {
                    // Load by slot index
                    ga.loadArg(i);
                }
                cons.visitMethodInsn(INVOKESPECIAL, getType(superType).getInternalName(), "<init>",
                        base.toMethodType(void.class).descriptorString(), false);
            }

            int index = base.args().size();
            for (MethodParameter ar : closureArgs) {
                ClosureRecord cr = closureSymbols.get(ar.name());
                // Constructor field
                cons.visitVarInsn(ALOAD, 0);
                ga.loadArg(index);
                ga.putField(Type.getType("L" + cs.getCurrentInternalName() + ";"), cr.memberName(), 
                        ar.getCompilableType());
                ++index;
            }

            cons.visitInsn(RETURN);
            cons.visitMaxs(0, 0);
            cons.visitEnd();
        } finally {
            cs.leaveMethod();
        }
    }

    private List<MethodParameter> toArgRecord(Map<String, ClosureRecord> closureSymbols) {
        return closureSymbols.entrySet().stream()
                .map(entry -> new MethodParameter(entry.getKey(), entry.getValue().type()))
                .toList();
    }

    private void defineClosureConstructor(CompilerState cs, ClassVisitor writer) {

        Map<String, ClosureRecord> closureSymbols = cs.getClosureSymbols();
        
        List<Class<?>> closureTypes = mapL(closureSymbols.values(), cr -> toCompilableType(cr.type()));
        MethodType methodType = methodType(void.class, closureTypes);

        MethodVisitor cons = writer.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "<init>", methodType.descriptorString(),
                null, null);

        cons.visitCode();
        cons.visitVarInsn(ALOAD, 0);
        cons.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int index = 1;
        for (Entry<String, ClosureRecord> f : closureSymbols.entrySet()) {
            Class<?> type = toCompilableType(f.getValue().type());
            
            // FIXME the indexes here are probably wrong, use GA methods instead.
            cons.visitVarInsn(ALOAD, 0);
            cons.visitVarInsn(ALOAD, index);
            cons.visitFieldInsn(PUTFIELD, cs.getCurrentInternalName(), f.getValue().memberName(),
                    Type.getDescriptor(type));
            ++index;
        }

        cons.visitInsn(RETURN);
        cons.visitMaxs(0, 0);
        cons.visitEnd();
    }

    private static MethodHandle bind(MethodHandle handle, Object base) {
        return handle.bindTo(base);
    }

    public static void defineConstructor(CompilerState cs, ParameterList pr) {
        
        MethodVisitor cons = cs.enterMethod("<init>", void.class, ACC_PUBLIC, pr);
        try {
            GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
    
            cons.visitCode();
            cons.visitVarInsn(ALOAD, 0);
            cons.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    
            int index = 0;
            for (MethodParameter ar : pr.args()) {
                // Constructor field
                cons.visitVarInsn(ALOAD, 0);
                ga.loadArg(index);
                ga.putField(Type.getType("L" + cs.getCurrentInternalName() + ";"), ar.name(), ar.getCompilableType());
                ++index;
            }
    
            cons.visitInsn(RETURN);
            cons.visitMaxs(0, 0);
            cons.visitEnd();
        } finally {
            cs.leaveMethod();
        }
    
    }

    public static void printDebug(byte[] classArray) {
        if (LOG.isEnabled(LogLevel.TRACE)) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintWriter printWriter = new PrintWriter(baos, true, StandardCharsets.UTF_8);) {
                printWriter.println("Compiled class:");

                ClassReader classReader = new ClassReader(classArray);
                TraceClassVisitor traceClassVisitor = new TraceClassVisitor(printWriter);
                classReader.accept(traceClassVisitor, 0);

                String msg = new String(baos.toByteArray(), StandardCharsets.UTF_8);

                LOG.trace("%s", msg);
            } catch (IOException e) {
                throw shouldNotHappen(e);
            }
        }
    }

    public static MethodArity collectPileMethods(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        Map<Integer, MethodHandle> airityHandles = new HashMap<>();
        MethodHandle varArgsMethod = null;
        int varArgsSize = -1;

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GeneratedMethod.class)) {
                MethodHandle handle = MethodHandles.lookup().unreflect(m);

                boolean isVarArgs = m.isAnnotationPresent(PileVarArgs.class);
                if (isVarArgs) {
                    varArgsMethod = handle;
                    // base ... iseq (2)
                    varArgsSize = handle.type().parameterCount() - 2;
                } else {
                    int mCount = m.getParameterCount();
                    airityHandles.put(mCount, handle);
                }
            }
        }

        // TODO kwargs unrolled
        return new MethodArity(airityHandles, varArgsMethod, varArgsSize, null);
    }
}
