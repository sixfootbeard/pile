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
package pile.core.parse;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentMap;
import pile.core.Metadata;

public record ParserResult(Object result, TypeTag tag, PersistentMap metadata) implements Metadata {
	
	public ParserResult(Object result, TypeTag tag) {
		this(result, tag, PersistentHashMap.EMPTY);
	}

	@Override
	public PersistentMap meta() {
		return metadata();
	}

	@Override
	public ParserResult withMeta(PersistentMap newMeta) {
		return new ParserResult(result, tag, newMeta);
	}
	
	public ParserResult withResult(Object newResult) {
		return new ParserResult(newResult, tag, metadata);
				
	}
}
