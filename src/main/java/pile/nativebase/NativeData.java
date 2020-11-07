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
package pile.nativebase;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import pile.collection.PersistentMap;
import pile.core.exception.PileInternalException;
import pile.core.indy.DebugHelpers;

/**
 * See pile.core.data
 *
 */
public class NativeData {

    private static final MethodHandle RECORD_MAP_HANDLE, MAP_GET, TO_RECORD_HANDLE;
    static {
        try {
            RECORD_MAP_HANDLE = lookup().findStatic(NativeData.class, "record_map",
                    methodType(PersistentMap.class, Object.class));
            TO_RECORD_HANDLE = lookup().findStatic(NativeData.class, "to_record",
                    methodType(Object.class, PersistentMap.class, Class.class));
            MAP_GET = lookup().findVirtual(PersistentMap.class, "get", 
                    methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new PileInternalException("Shouldn't happen.");
        }
    }

    private NativeData() {}

    public static final PersistentMap record_map(Object v) throws Throwable {
        Map<String, MethodHandle> getters = Holder.GETTERS.get(v.getClass());
        if (getters == null) {
            getters = createGetters(v.getClass());
        }
        PersistentMap out = PersistentMap.empty();
        for (var kv : getters.entrySet()) {
            String key = kv.getKey();
            MethodHandle getter = kv.getValue();
            out = out.assoc(keyword(key), getter.invoke(v));
        }
        return out;
    }

    private static Map<String, MethodHandle> createGetters(Class<? extends Object> clz) throws IllegalAccessException {
        Map<String, MethodHandle> getters = new HashMap<>();
        Lookup lookup = lookup();
    
        RecordComponent[] recordComponents = clz.getRecordComponents();
        ensure(recordComponents != null, clz + " is not a record");
        for (var rc : recordComponents) {
            Class<?> fieldType = rc.getType();
            Method method = rc.getAccessor();
            MethodHandle handle = lookup.unreflect(method);
            
            if (fieldType.isRecord()) {
                MethodType typed = RECORD_MAP_HANDLE.type().changeParameterType(0, handle.type().returnType());
                handle = MethodHandles.filterReturnValue(handle, RECORD_MAP_HANDLE.asType(typed));
            }
            getters.put(rc.getName(), handle);
        }
        Holder.GETTERS.putIfAbsent(clz, getters);
        return getters;
    }

    public static final Object to_record(PersistentMap map, Class clz) throws Throwable {
        SetRec setters = Holder.SETTERS.get(clz);
        if (setters == null) {
            setters = createSetters(clz);
        }
        List<Object> part = new ArrayList<>();
        for (var h : setters.accessors()) {
            part.add(h.invoke(map));
        }
        return setters.cons().invokeWithArguments(part);
    }

    private static SetRec createSetters(Class<? extends Object> clz)
            throws IllegalAccessException, NoSuchMethodException, SecurityException {
        List<MethodHandle> order = new ArrayList<>();
        List<Class> types = new ArrayList<>();

        Lookup lookup = lookup();

        RecordComponent[] recordComponents = clz.getRecordComponents();
        ensure(recordComponents != null, clz + " is not a record");
        for (var rc : recordComponents) {
            Class<?> fieldType = rc.getType();
            String name = rc.getName();
            MethodHandle handle = MAP_GET;
            if (fieldType.isRecord()) {
                final MethodHandle bound = insertArguments(TO_RECORD_HANDLE, 1, fieldType);
                final MethodHandle typedGet = handle.asType(handle.type().changeReturnType(PersistentMap.class));

                final MethodHandle maybeDebugHandle;
                if (DebugHelpers.isDebugEnabled()) {
                    maybeDebugHandle = DebugHelpers.catchFormatted(typedGet, "Expected value at key %2$s in '%1$s' to be a " + PersistentMap.class);
                } else {
                    maybeDebugHandle = typedGet;
                }
                final MethodHandle withKey = insertArguments(maybeDebugHandle, 1, keyword(name));
                handle = MethodHandles.filterReturnValue(withKey, bound);
            } else {
                handle = insertArguments(handle, 1, keyword(name));
            }
            order.add(handle);
            types.add(fieldType);
        }

        Constructor<? extends Object> cons = clz.getDeclaredConstructor(types.toArray(Class[]::new));

        MethodHandle mhCons = lookup.unreflectConstructor(cons);

        SetRec setRec = new SetRec(order, mhCons);

        Holder.SETTERS.putIfAbsent(clz, setRec);
        return setRec;
    }

    private record SetRec(List<MethodHandle> accessors, MethodHandle cons) {
    }

    private static class Holder {
        private static final ConcurrentHashMap<Class, Map<String, MethodHandle>> GETTERS = new ConcurrentHashMap<>();
        private static final ConcurrentHashMap<Class, SetRec> SETTERS = new ConcurrentHashMap<>();
    }

}
