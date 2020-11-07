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

import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentMap;
import pile.core.binding.Binding;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.indy.PileMethodLinker;

public class NativeValue<T> implements Binding<T> {

    public static final NativeValue<Hierarchy> HIERARCHY = new NativeValue<>("default-hierarchy", new Hierarchy());

    private static final PersistentMap META = PersistentMap.createArr(PileMethodLinker.FINAL_KEY, true);

    private final String name;
    private final T val;

    public NativeValue(String name) {
        this(name, null);
    }

    public NativeValue(String name, T initial) {
        this.name = name;
        this.val = initial;
    }

    @Override
    public PersistentMap meta() {
        return META;
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return name;
    }

    @Override
    public T getValue() {
        return val;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return null;
    }

    @Override
    public String namespace() {
        return "pile.core";
    }

    public static NativeValue[] values() {
        return new NativeValue[] { HIERARCHY };
    }

}
