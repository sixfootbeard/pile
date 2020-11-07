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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Attempts to parse using a list of readers, returning the first that produces
 * a result.
 *
 */
public class OneOfReader implements Reader {

    private final List<Reader> readers;

    public OneOfReader(Reader... r) {
        this.readers = Arrays.asList(r);
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
        for (Reader reader : readers) {
            Optional<ParserResult> result = reader.parse(env, pr);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

}
