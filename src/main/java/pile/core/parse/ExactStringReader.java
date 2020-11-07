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
import java.util.Optional;

/**
 * Attempts to read a specific string. Returns that string if found, or empty if
 * the string is not read exactly.
 * 
 */
public class ExactStringReader implements Reader {

    private final char[] c;
    private final TypeTag tag;
    private final String str;

    private char[] buf;

    public ExactStringReader(String c, TypeTag tag) {
        this.str = c;
        this.c = c.toCharArray();
        this.buf = new char[this.c.length];
        this.tag = tag;
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
        for (int i = 0; i < c.length; ++i) {
            var read = pr.read();
            if (read == -1) {
                pr.unread(buf, 0, i);
                return Optional.empty();
            }
            buf[i] = (char) read;
            if (buf[i] != c[i]) {
                pr.unread(buf, 0, i + 1);
                return Optional.empty();
            }
        }
        env.incRead(str.length());
        return Optional.of(new ParserResult(str, tag));
    }

}
