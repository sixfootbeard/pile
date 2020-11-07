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

import java.io.IOException;
import java.io.PushbackReader;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Readers a character and then looks up a reader in the supplied reader map to
 * delegate parsing to.
 *
 */
public class CharTokenDispatchReader implements Reader {

    private final Map<Character, Supplier<? extends Reader>> map;

    public CharTokenDispatchReader(Map<Character, Supplier<? extends Reader>> map) {
        this.map = map;
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
        int read = pr.read();
        char c = (char) read;
        Supplier<? extends Reader> supplier = map.get(c);
        if (supplier != null) {
            env.incRead();
            Reader reader = supplier.get();
            return reader.parse(env, pr);
        } else {
            pr.unread(read);
            return Optional.empty();
        }
    }

}
