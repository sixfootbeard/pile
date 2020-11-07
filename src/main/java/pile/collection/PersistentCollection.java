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
package pile.collection;

import java.io.Serializable;

import org.objectweb.asm.ConstantDynamic;

import pile.core.ConstForm;
import pile.core.Metadata;
import pile.core.PCall;
import pile.core.PileMethod;
import pile.core.Seqable;

public interface PersistentCollection<E>
        extends Counted, Seqable<E>, Metadata, PileMethod, ConstForm<ConstantDynamic>, Serializable {

}
