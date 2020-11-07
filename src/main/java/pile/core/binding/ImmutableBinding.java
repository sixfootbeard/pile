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

import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentMap;
import pile.core.Metadata;

public record ImmutableBinding(String ns, BindingType type, Object ref, PersistentMap meta, SwitchPoint sp)
        implements Binding {
        
    public ImmutableBinding(String ns, Object ref) {
        this(ns, BindingType.VALUE, ref, PersistentMap.EMPTY, new SwitchPoint());
    }

    @Override
    public PersistentMap meta() {
        return meta.assoc(Binding.BINDING_TYPE_KEY, type);
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        return new ImmutableBinding(ns, type, ref, newMeta, sp);
    }

    @Override
    public Object getValue() {
        return ref;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return sp;
    }

    @Override
    public String namespace() {
        return ns;
    }

    @Override
    public ImmutableBinding withSwitchPoint(SwitchPoint sp) {
        return new ImmutableBinding(ns, type, ref, meta, sp);
    }
    
}
