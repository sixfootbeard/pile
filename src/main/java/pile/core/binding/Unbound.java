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
import pile.core.indy.PileMethodLinker;

public class Unbound implements Binding {

    private final boolean isFinal;
    private final boolean isMacro;
    private final String ns;
    private final SwitchPoint sp = new SwitchPoint();

    private Unbound() {
        this(false);
    }

    private Unbound(boolean isFinal) {
        this(null, isFinal);
    }

    public Unbound(String ns, boolean isFinal) {
        this(ns, isFinal, false);
    }

    public Unbound(String ns, boolean isFinal, boolean isMacro) {
        this.ns = ns;
        this.isFinal = isFinal;
        this.isMacro = isMacro;
    }

    @Override
    public PersistentMap meta() {
        return PersistentMap.createArr(PileMethodLinker.FINAL_KEY, isFinal, PileMethodLinker.MACRO_KEY, isMacro,
                Binding.BINDING_TYPE_KEY, BindingType.UNBOUND);
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        throw new IllegalStateException();
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        // A method may try to link against an unbound binding but it should never get
        // to the point of needing a switchpoint.
        return null;
    }

    @Override
    public String namespace() {
        return ns;
    }

    public static boolean isUnbound(Binding b) {
        return (b instanceof Unbound);
    }

}
