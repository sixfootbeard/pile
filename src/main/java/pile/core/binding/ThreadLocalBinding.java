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
package pile.core.binding;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.SwitchPoint;
import java.util.concurrent.TimeUnit;

import pile.collection.PersistentMap;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.SettableRef;
import pile.core.indy.CompilerFlags;

public class ThreadLocalBinding<T> implements Binding<T>, SettableRef<T> {

    private final ThreadLocal<T> threadLocal;
    private final String ns;
    private final String name;
    private final PersistentMap meta;
    private final SwitchPoint sp;

    public ThreadLocalBinding(String ns, String name, T initial, PersistentMap meta, SwitchPoint sp) {
        this.ns = ns;
        this.name = name;
        this.threadLocal = ThreadLocal.withInitial(() -> initial);
        this.meta = meta.assoc(Binding.BINDING_TYPE_KEY, BindingType.DYNAMIC);
        this.sp = sp;
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public ThreadLocalBinding<T> withMeta(PersistentMap newMeta) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return sp;
    }

    @Override
    public T getValue() {
        return threadLocal.get();
    }
    
    @Override
    public T deref() {
        return getValue();
    }
    
    @Override
    public T deref(long time, TimeUnit unit) {
        return deref();
    }

    @Override
    public void set(T newRef) {
        threadLocal.set(newRef);
    }

    public String getName() {
        return name;
    }

    @Override
    public String namespace() {
        return ns;
    }

    @Override
    public void update(PCall fn) throws Throwable {
        // no contention
        T t = getValue();
        T out = (T) fn.invoke(t);
        set(out);
    }

}
