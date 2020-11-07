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

public class TripleQuoteStringFormReader implements Reader {

    public TripleQuoteStringFormReader() {
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
        LexicalEnvironment lex = env.snapshot();
        if (! readTripleQuote(pr)) {
            return Optional.empty();
        }
        env.incRead(3);
        int newLine = pr.read();
        if (newLine != '\n') {
            throw lex.makeError("Triple quote string header must end with a newline");
        }
        env.newline();
        eatIndentation(env, pr);
        
        boolean done = false;
        StringBuilder sb = new StringBuilder();
        outer:
        while (! done) {
            int read = pr.read();
            switch (read) {
                case -1: throw lex.makeError("Unexpected EOF while reading string literal.");
                case '\n':
                    sb.append((char)read);
                    env.newline();
                    eatIndentation(env, pr);
                    break;
                case '"': 
                    pr.unread('"');
                    if (readTripleQuote(pr)) {
                        env.incRead(3);
                        break outer;
                    }
                    // fall through                 
                default: 
                    sb.append((char)read);
                    env.incRead();
                    break;
            }
        }
        
        ParserResult result = new ParserResult(sb.toString(), TypeTag.STRING);
        return Optional.of(result);
    }
    
    private void eatIndentation(LexicalEnvironment current, PushbackReader pr) throws IOException {
        outer: 
        for (;;) {
            int read = pr.read();
            switch (read) {
                case ' ':
                case '\t': 
                    // fine
                    current.incRead(); 
                    break;
                default:
                    // user supplied some chars, bail.
                    pr.unread(read);
                    break outer;
            }
        }
        
    }

    private boolean readTripleQuote(PushbackReader pr) throws IOException {
        for (int i = 0; i < 3; ++i) {
            int read = pr.read();
            if (read != '"') {
                pr.unread(read);
                for (int u = 0; u < i; ++u) {
                    pr.unread('"');
                }
                return false;
            }
        }
        return true;
    }

}
