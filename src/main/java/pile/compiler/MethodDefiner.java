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

import static java.lang.invoke.MethodType.*;
import static java.util.Objects.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.MethodCollector.MethodArity;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.annotation.GeneratedMethod;
import pile.compiler.annotation.PileVarArgs;
import pile.compiler.form.AnonClassForm;
import pile.compiler.form.DefTypeForm;
import pile.compiler.form.InteropForm;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.exception.PileCompileException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.LexicalEnvironment;
import pile.core.runtime.generated_classes.LookupHolder;

/**
 * Defines methods in a particular type that may require special care to match
 * java type signatures or handle (name, arity) collisions.
 * 
 * @see DefTypeForm
 * @see AnonClassForm
 * @see InteropForm
 * 
 */
public class MethodDefiner {
    
    private static final Logger LOG = LoggerSupplier.getLogger(MethodDefiner.class);

    public record MethodRecord(String name, ParameterList args, PersistentList body) {
    }
    
    public record MethodArgsBody(ParameterList args, PersistentList body) {    
        public MethodRecord withName(String name) {
            return new MethodRecord(name, args, body);
        } 
    }

    public record ActualMethod(String name, ParameterList args) {
    }

    private static final Type ISEQ_TYPE = getType(ISeq.class);

    public MethodDefiner() {
    }

    public Map<MethodRecord, ActualMethod> defineMethods(CompilerState cs, AbstractClassCompiler comp, List<Class<?>> interfaces,
            List<MethodRecord> methods) {
        return defineMethods(cs, comp, null, interfaces, methods);
    }

    public Map<MethodRecord, ActualMethod> defineMethods(CompilerState cs, AbstractClassCompiler comp, Class<?> maybeSuperType,
           List<Class<?>> interfaces, List<MethodRecord> methods) {
        
        // @formatter:off
        record MethodResult(String methodName, int arity) {};
        Map<MethodRecord, ActualMethod> renames = new HashMap<>();
        Map<MethodResult, List<Method>> abstractMethods = new HashMap<>();
        
        var superType = maybeSuperType == null ? Object.class : maybeSuperType;
        
        Arrays.stream(superType.getMethods())
              .filter(m -> Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers()))
              .filter(m -> ! Modifier.isStatic(m.getModifiers()))
              .forEach(m -> {
                  MethodResult mr = new MethodResult(m.getName(), m.getParameterCount());
                  abstractMethods.computeIfAbsent(mr, k -> new ArrayList<>()).add(m);
              });            

        interfaces.stream()
            .map(Class::getMethods)
            .flatMap(Arrays::stream)
            .filter(m -> ! Modifier.isStatic(m.getModifiers()))
            .forEach(m -> {
                MethodResult mr = new MethodResult(m.getName(), m.getParameterCount());
                abstractMethods.computeIfAbsent(mr, k -> new ArrayList<>()).add(m);
            });
        // @formatter:on

        for (MethodRecord mr : methods) {
            // (name [args] body)
            String methodName = mr.name();
            ParameterList pr = mr.args();
            List<MethodParameter> parseArgs = pr.args();
            int size = parseArgs.size();
            PersistentList body = mr.body();            

            List<Method> candidateMethods = abstractMethods.remove(new MethodResult(methodName, size - 1));
            requireNonNull(candidateMethods, () -> "Unknown method name and arity: " + methodName + ":" + size);

            if (candidateMethods.size() == 1) {
                // Ideally we'd like to enrich the user provided methods with type information:
                // public String foo(Long bar);
                // (foo [bar])
                // ->
                // ^String (foo [^Long bar])

                var method = candidateMethods.get(0);
                if (method.isVarArgs() ^ pr.isVarArgs()) {
                    String msg = "User provided method must match varargs for supertype method %s";
                    throw new PileCompileException(String.format(msg, method));
                }
                Class<?> returnType = method.getReturnType();
                ParameterList instancePr = pr.updateTypesAndReceiver(method).popFirst();

                if (pr.isVarArgs()) {
                    // Special case - Varargs
                    // public String bar(int f, String... vars)
                    // (bar [f & var] ...)
                    // Create a separate method to forward to after wrapping the suffix array args
                    // in an ISeq. This is for because of a different varargs implementation (array
                    // vs ISeq suffix)

                    ParameterList pileVarArgs = instancePr.modifyLastArg(last -> new MethodParameter(last.name(), ISeq.class));

                    // Create user defined method w/ ISeq
                    String syntheticMethodName = method.getName() + "$varargs";
                    comp.createSingleMethod(cs, syntheticMethodName, returnType, pileVarArgs, List.of(),
                            ACC_PRIVATE | ACC_SYNTHETIC, body);

                    // Create delegate method
                    int delegateModifiers = method.getModifiers() & (~ACC_ABSTRACT);
                    comp.createSingleMethodCustom(cs, methodName, returnType, instancePr, List.of(), delegateModifiers,
                            m -> {
                                GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
                                ga.loadThis();
                                for (int i = 0; i < instancePr.args().size(); ++i) {
                                    ga.loadArg(i);
                                }
                                // Last arg is in position
                                MethodVisitor mv = cs.getCurrentMethodVisitor();
                                mv.visitMethodInsn(INVOKESTATIC, Type.getType(ISeq.class).getInternalName(), "of",
                                        getMethodDescriptor(ISEQ_TYPE, OBJECT_ARRAY_TYPE), true);
                                // TODO Need to fix the rest of the primitive object types
                                // TODO Probably broke receiver type here
                                org.objectweb.asm.commons.Method syntheticMethod = new org.objectweb.asm.commons.Method(
                                        syntheticMethodName, pileVarArgs.toMethodType(returnType).descriptorString());
                                // Call the other method
                                ga.invokeVirtual(Type.getType("L" + comp.getInternalName() + ";"), syntheticMethod);
                                cs.getMethodStack().push(returnType);
                                ga.returnValue();
                            });
                    renames.put(mr, new ActualMethod(syntheticMethodName, instancePr));
                } else {
                    comp.createSingleMethod(cs, method.getName(), returnType, instancePr, List.of(), body);
                    renames.put(mr, new ActualMethod(mr.name(), instancePr));
                }
            } else {
                // For overloaded methods there could be multiple matches:
                // private Date foo(String bar)
                // private Date foo(Long bar)
                // (foo [arg] ...)
                // In this case we emit N bridge methods for each typed variant.
                // Those methods all call an untyped implementation:
                // private Object foo(Object o) { // user implementation }
                // Bridges:
                // private Date foo(String bar) { return (Date) foo((Object)o); }
                // private Date foo(long bar) { return (Date) foo((Object)bar); // with boxing }

                // TODO Varargs
                // - normal
                // - different size
                // private Date foo(String bar, int... parts)
                // private Date foo(long... bar)
                // - different vararg state
                // private Date foo(String bar)
                // private Date foo(long... bar)

                // TODO Doesn't handle same typed method/name in different interfaces yet
                var privateTarget = methodName + "$pile";
                for (Method m : candidateMethods) {
                    ParameterList updatedParsedRecord = pr.updateTypesAndReceiver(m).popFirst();
                    comp.createSingleMethodCustom(cs, methodName, m.getReturnType(), updatedParsedRecord, List.of(),
                            ACC_PUBLIC, method -> {
                                GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
                                Class<?> returnType = m.getReturnType();

                                ga.loadThis();
                                List<MethodParameter> updatedArgs = updatedParsedRecord.args();
                                for (int i = 0; i < updatedArgs.size(); ++i) {
                                    ga.loadArg(i);
                                    MethodParameter arg = updatedArgs.get(i);
                                    ga.box(arg.getCompilableType());
                                }
                                // call generic fn
                                String internalName = comp.getInternalName();
                                String genericDescriptor = updatedParsedRecord.genericCompilable().toMethodType(Object.class)
                                        .descriptorString();
                                ga.visitMethodInsn(INVOKEVIRTUAL, internalName, privateTarget, genericDescriptor,
                                        false);
                                ga.checkCast(getType(returnType));
                                cs.getMethodStack().push(returnType);
                                ga.returnValue();
                            });
                }

                // Users implementation
                ParameterList genericParseRecord = pr.popFirst().generic();
                comp.createSingleMethod(cs, privateTarget, ANY_CLASS, genericParseRecord, List.of(), ACC_PRIVATE,
                        body);
                renames.put(mr, new ActualMethod(privateTarget, genericParseRecord));
            }
        }
        for (var m : abstractMethods.entrySet()) {
            for (var method : m.getValue()) {
                if (method.isDefault()) {
                    continue;
                }
                if (isObjectMethod(method)) {
                    continue;
                }
                LOG.warn("Missing impl for anon-class: %s.%s", method.getDeclaringClass().getName(), m.getKey().methodName());
                break;
            }            
        }
        return renames;

    }

    public static MethodArity collectPileMethods(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        TreeMap<Integer, MethodHandle> airityHandles = new TreeMap<>();
        MethodHandle varArgsMethod = null;
        int varArgsSize = -1;
    
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GeneratedMethod.class)) {
                MethodHandle handle = LookupHolder.PRIVATE_LOOKUP.unreflect(m);
    
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

    /**
     * Parse a function definition into a list of records that all represent
     * distinct methods.
     * 
     * @param ns
     * @param top
     * @return
     */
    public static List<MethodArgsBody> parse(Namespace ns, PersistentList top) {
        // ([args] body)
        // (([args] body) ([args two] body))
        
        Object argsOrSexpr = first(top);
        if (argsOrSexpr instanceof PersistentList pl) {
            List<MethodArgsBody> out = new ArrayList<>();
            for (var raw : top) {
                PersistentList innerList = expectList(raw);
                var vec = expectVector(first(innerList));
                var body = innerList.pop();
                ParameterParser parser = new ParameterParser(ns, vec);
                out.add(new MethodArgsBody(parser.parse(), body));
            }
            return out;
        } else if (argsOrSexpr instanceof PersistentVector pv) {
            PersistentList body = top.pop();
            ParameterParser parser = new ParameterParser(ns, pv);
            return List.of(new MethodArgsBody(parser.parse(), body));
        } else {
            throw new PileCompileException("Unexpected method style", LexicalEnvironment.extract(top));
        }
    }
    
    public static List<MethodRecord> parseNamed(Namespace ns, PersistentList top) {
        // (name [args] body)
        // (name ([args] body) ([args two] body))
        String method = strSym(first(top));
        return parse(ns, top.pop()).stream()
                    .map(mab -> mab.withName(method))
                    .toList();
    }
        
    private static final Map<String, Set<MethodType>> OBJECT_METHODS;
    
    static {
        Map<String, Set<MethodType>> local = new HashMap<>();
        for (var m : Object.class.getMethods()) {
            int modifiers = m.getModifiers();
            if ((modifiers & (Modifier.PUBLIC)) > 0 ||
                    (modifiers & (Modifier.PROTECTED)) > 0) {
                MethodType methodType = methodType(m.getReturnType(), m.getParameterTypes());
                local.computeIfAbsent(m.getName(), k -> new HashSet<>()).add(methodType);
            }
        }
        OBJECT_METHODS = Collections.synchronizedMap(local);
    }
    
    private static boolean isObjectMethod(Method method) {
        // Some methods can be redefined in abstract classes lower than their original
        // definition point (eg. Object.equals -> Comparator.equals) so this check is
        // not sufficient for determining if a method was originally an object method.
        if (method.getDeclaringClass().equals(Object.class)) {
            return true;
        }
        
        Set<MethodType> maybeTypes = OBJECT_METHODS.get(method.getName());
        if (maybeTypes != null) {
            MethodType methodType = methodType(method.getReturnType(), method.getParameterTypes());
            return maybeTypes.contains(methodType);
        }
        
        return false;
    }

}
