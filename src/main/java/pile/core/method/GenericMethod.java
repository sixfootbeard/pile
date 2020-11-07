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
package pile.core.method;

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.StaticTypeLookup;
import pile.compiler.typed.TypeVarArg;
import pile.compiler.typed.TypedHelpers;
import pile.core.PileMethod;
import pile.util.Pair;

public class GenericMethod implements PileMethod {


    private final AtomicReference<GenericMethodRecord> state;

    private final Set<Integer> arities;
    private final int varArgsArity;
    
    // arity = 1 only
    private Class<?> ifaceClass;
    private final MethodHandle ifaceMethod;
    private final String methodName;

    public GenericMethod(GenericMethodTargets targets) {
        super();
        this.state = new AtomicReference<>(new GenericMethodRecord(targets.varArgsArity()));
        this.arities = Set.copyOf(targets.arities());
        this.varArgsArity = targets.varArgsArity();
        this.ifaceMethod = null;
        this.ifaceClass = null;
        this.methodName = null;
    }

    public GenericMethod(Class<?> clazz, String methodName, MethodHandle value) {
        // single 1-arity method w/ interface
        this.state = new AtomicReference<>(new GenericMethodRecord());
        this.arities = Set.of(1);
        this.varArgsArity = -1;
        this.ifaceMethod = value;
        this.ifaceClass = clazz;
        this.methodName = methodName;
    }

    @Override
    public boolean acceptsArity(int arity) {
        return arities.contains(arity);
    }
    
    public Class<?> getIfaceClass() {
        return ifaceClass;
    }
    
    public String getMethodName() {
        return methodName;
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        var local = state.get();
        var argClasses = getArgClasses(args);
        if (argClasses.size() == 1 && 
                ifaceClass != null && ifaceClass.isAssignableFrom(argClasses.get(0))) {
            return ifaceMethod.invoke(args[0]);
        }
        PersistentList<MethodTarget> methods = local.fns().get(args.length);
        if (methods != null) {
            // FIXME Object return type
            DynamicTypeLookup<MethodTarget> dyn = new DynamicTypeLookup<>(
                    mt -> new TypeVarArg(methodType(Object.class, mt.types()), false));
            MethodTarget target = dyn.findMatchingTarget(argClasses, methods.stream())
                                    .orElseThrow(() -> new IllegalArgumentException("No matching types"));
            return target.method().invoke(args);
        } else {
            if (varArgsArity == -1) {
                throw new NoSuchMethodError("Cannot call methods with types: " + argClasses);
            } else {
                PersistentList<MethodTarget> varArgsMethods = local.varArgs();
                DynamicTypeLookup<MethodTarget> dyn = new DynamicTypeLookup<>(GenericMethod::toVarArgTVA);
                MethodTarget target = dyn.findMatchingTarget(argClasses, varArgsMethods.stream())
                                        .orElseThrow(() -> new IllegalArgumentException("No matching types"));
//                HandleLookup result = new HandleLookup(HandleType.VARARGS, args.length, target.method());
                return target.method().invoke(args);
            }   
        }
        
    }
    
    private static TypeVarArg toVarArgTVA(MethodTarget type) {
        List<Class<?>> types = new ArrayList<>(type.types());
        types.set(types.size() - 1, Object[].class);
        return new TypeVarArg(methodType(Object.class, types), true);
    }

    public void update(List<Class<?>> types, PileMethod compiledFunction) {
        MethodTarget mt = new MethodTarget(compiledFunction, types);
        for (;;) {
            GenericMethodRecord old = state.get();
            GenericMethodRecord record = old.withMethod(mt);            
            if (state.compareAndSet(old, record)) {
                SwitchPoint.invalidateAll(new SwitchPoint[] { old.sp() });
                break;
            }
        }        
    }
    
    public void updateVarArgs(List<Class<?>> types, PileMethod compiledFunction) {
        MethodTarget mt = new MethodTarget(compiledFunction, types);
        for (;;) {
            GenericMethodRecord old = state.get();
            GenericMethodRecord record = old.withVarArgsMethod(mt);            
            if (state.compareAndSet(old, record)) {
                SwitchPoint.invalidateAll(new SwitchPoint[] { old.sp() });
                break;
            }
        }        
    }

    public record GenericMethodTargets(Set<Integer> arities, int varArgsArity) {}

    private record MethodTarget(PileMethod method, List<Class<?>> types) {
    }

    private record GenericMethodRecord(SwitchPoint sp, PersistentMap<Integer, PersistentList<MethodTarget>> fns, int varArgsArity,
           PersistentList<MethodTarget> varArgs) {
           
        public GenericMethodRecord() {
            this(new SwitchPoint(), PersistentMap.EMPTY, -1, PersistentList.EMPTY);
        }
        
        public GenericMethodRecord(int varArgsArity) {
            this(new SwitchPoint(), PersistentMap.EMPTY, varArgsArity, PersistentList.EMPTY);
        }
           
        public GenericMethodRecord withMethod(MethodTarget tgt) {
            int size = tgt.types().size();
            PersistentList<MethodTarget> targets = fns.get(size);
            if (targets == null) {
                targets = PersistentList.EMPTY;
            }
            PersistentList<MethodTarget> newTargets = targets.conj(tgt);
            PersistentMap<Integer, PersistentList<MethodTarget>> newMap = fns.assoc(size, newTargets);
            GenericMethodRecord record = new GenericMethodRecord(new SwitchPoint(), newMap, varArgsArity, varArgs);
            return record;
        }
        
        public GenericMethodRecord withVarArgsMethod(MethodTarget tgt) {
            ensure(varArgsArity != -1, "No varargs method to override");
            
            int size = tgt.types().size();
            ensure(varArgsArity == size, "Wrong size varargs method to override.");
            PersistentList<MethodTarget> targets = varArgs.conj(tgt);
            GenericMethodRecord record = new GenericMethodRecord(new SwitchPoint(), fns, varArgsArity, targets);
            return record;
        }
    }

}
