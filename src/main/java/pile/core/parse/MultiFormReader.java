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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pile.collection.PersistentList;

/**
 * Parses a sequence of results. First, tests the 'end' condition, and if not
 * met parses the 'each' term. The intermediate term must always return results
 * as long as the end test doesn't match. Returns a {@link ParserResult} of a
 * {@link PersistentList} of all intermediate elements that were returned.
 *
 */
public class MultiFormReader implements Reader {

    private final TypeTag tag;
    private final Reader each;
    private final Reader endTest;

    public MultiFormReader(TypeTag tag, Reader each, Reader endTest) {
        this.each = each;
        this.endTest = endTest;
        this.tag = tag;
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
        LexicalEnvironment lex = env.snapshot();

        List<Object> list = new ArrayList<>();
        for (;;) {
            PileParser.skipWhitespace(env, pr);
            Optional<ParserResult> maybeEnd = endTest.parse(env, pr);
            if (maybeEnd.isEmpty()) {
                Optional<ParserResult> maybeMiddle = each.parse(env, pr);
                if (maybeMiddle.isPresent()) {
                    ParserResult middleResult = maybeMiddle.get();
                    // Masking errors with null seems sus
                    if (middleResult != null) {
                        list.add(middleResult.result());
                    }
                } else {
                    throw env.makeError("Found unexpected end of multi-form sequence.");
                }
            } else {
                break;
            }
        }

        PersistentList<Object> out = PersistentList.fromList(list);
        out = lex.enrich(out);

        return Optional.of(lex.enrich(new ParserResult(out, tag)));
    }

}
