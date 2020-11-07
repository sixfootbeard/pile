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
package pile.core;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import pile.collection.PersistentMap;
import pile.core.binding.Binding;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.method.LinkableMethod;

public class FinalVar<T> extends AbstractVar<T> {

    private final Binding<T> bind;

    public FinalVar(Namespace ns, String name, Binding<T> bind) {
        super(ns, name);
        this.bind = bind;
    }

    @Override
    public T deref() {
        return bind.getValue();
    }

    @Override
    public T deref(long time, TimeUnit unit) throws Throwable {
        return deref();
    }

    @Override
    public PersistentMap meta() {
        return bind.meta();
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        throw new UnsupportedOperationException("Cannot update meta of a final var");
    }
    
    @Override
    protected Binding<T> bind() {
        return bind;
    }

}
