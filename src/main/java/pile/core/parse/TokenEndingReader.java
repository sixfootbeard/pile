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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Parser that collects characters up until an end predicate returns false, and
 * then passes them to a function which produces a result.
 *
 * @param <T>
 */
class TokenEndingReader<T> implements Reader {

    private final Function<String, T> fn;
    private final TypeTag tag;
    private final Predicate<Character> end;

    public TokenEndingReader(TypeTag tag, Function<String, T> fn, Predicate<Character> doEnd) {
        this.tag = tag;
        this.fn = fn;
        this.end = doEnd;
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {

        StringBuilder sb = new StringBuilder();
        int c = pr.read();
        while (c != -1 && !end.test((char) c)) {
            env.incRead();
            sb.append((char) c); // TODO type casting?
            c = pr.read();
        }
        if (c != -1) {
            pr.unread(c);
        }
        ParserResult out = new ParserResult(fn.apply(sb.toString()), tag);
        return Optional.of(env.enrich(out));
    }

}