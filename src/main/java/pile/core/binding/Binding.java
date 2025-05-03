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

import static pile.nativebase.NativeCore.*;

import java.lang.invoke.SwitchPoint;

import pile.core.Ref;
import pile.collection.PersistentMap;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.Value;
import pile.core.indy.PileMethodLinker;

/**
 * A binding holds a {@link #getValue() value}. Bindings are
 * {@link Namespace#define(String, Binding) inserted} into a {@link Namespace}.
 * Bindings may be {@link PileMethodLinker#isFinal(Metadata) final} or
 * non-final. If they are final they may not be rebound. A binding has its own
 * {@link #getType(Binding) type} which indicates whether its value is
 * {@link BindingType#VALUE stable} or may change ({@link BindingType#DYNAMIC
 * explicitly} or {@link BindingType#REFERENCE implicitly}) each time it is
 * {@link #getValue() accessed}. A binding that is non-final *must* have a
 * {@link #getSwitchPoint() switchpoint}. Bindings also indicate whether the
 * value they hold is a {@link #isMacro() macro}.
 *
 */
public interface Binding<T> extends Metadata, Value<T> {

    public static final Keyword BINDING_TYPE_KEY = Keyword.of("binding-type");

    SwitchPoint getSwitchPoint();

    public static BindingType getType(Binding<?> bind) {
        return (BindingType) get(bind.meta(), BINDING_TYPE_KEY, BindingType.VALUE);
    }

    String namespace();

    default boolean isMacro() {
        return (boolean) get(meta(), PileMethodLinker.MACRO_KEY, false);
    }

    default Binding<T> withSwitchPoint(SwitchPoint sp) {
        throw new UnsupportedOperationException("Cannot swap SP");
    }

    public static Binding ofValue(Namespace ns, Object o) {
        return new ImmutableBinding(ns.getName(), BindingType.VALUE, o, PersistentMap.empty(), new SwitchPoint());
    }

}