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

import pile.collection.PersistentMap;
import pile.core.Metadata;
import pile.core.Namespace;

@SuppressWarnings("rawtypes")
public class ReferenceBinding implements Binding {

    private final Namespace ourNs;
    private final Namespace otherNs;
    private final String otherSym;
    private final PersistentMap meta;
    private final SwitchPoint sp = new SwitchPoint();

    public ReferenceBinding(Namespace ourNs, Namespace otherNs, String otherSym, PersistentMap meta) {
        super();
        this.ourNs = ourNs;
        this.otherNs = otherNs;
        this.otherSym = otherSym;
        this.meta = meta.assoc(BINDING_TYPE_KEY, BindingType.REFERENCE);
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public ReferenceBinding withMeta(PersistentMap newMeta) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Object getValue() {
        return getOtherBinding().getValue();
    }

    public Binding getOtherBinding() {
        Binding bind = Namespace.getIn(otherNs, otherSym);
        return bind;
    }
    
    @Override
    public boolean isMacro() {
        return getOtherBinding().isMacro();
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return sp;
    }

    @Override
    public String namespace() {
        return otherNs.getName();
    }

    public static Binding maybeDeref(Binding b) {
        for (;;) {
            if (Binding.getType(b) == BindingType.REFERENCE) {
                b = ((ReferenceBinding)b).getOtherBinding();
            } else {
                break;
            }
        }
        return b;
    }

}
