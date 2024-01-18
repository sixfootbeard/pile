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
package pile.core.hierarchy;

import java.util.function.Function;

import pile.collection.PersistentMap;
import pile.core.Metadata;

@SuppressWarnings("rawtypes")
public abstract class AbstractMetadataObject<T extends Metadata> implements Metadata {

    private final PersistentMap meta;

    public AbstractMetadataObject(PersistentMap meta) {
        this.meta = meta;
    }

    @Override
    public PersistentMap meta() {
        return meta;
    }

    @Override
    public abstract T withMeta(PersistentMap newMeta);

    @Override
    public T updateMeta(Function<PersistentMap, PersistentMap> update) {
        return withMeta(update.apply(meta));
    }

}
