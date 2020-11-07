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

import static pile.compiler.Helpers.*;

import java.io.IOException;
import java.io.PushbackReader;
import java.util.Optional;

public class StringFormReader implements Reader {
	
	private char[] unicode = new char[4];

	public StringFormReader() {
	}

	@Override
	public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
	    LexicalEnvironment lex = env.snapshot();
	    pr.unread('"');
	    
	    TripleQuoteStringFormReader triple = new TripleQuoteStringFormReader();
	    Optional<ParserResult> maybeTriple = triple.parse(env, pr);
	    if (maybeTriple.isPresent()) {
	        return maybeTriple;
	    }
	    
	    pr.read(); // '"'
	    
        StringBuilder sb = new StringBuilder();
        // "|foo\n"
        boolean inescape = false;
        int c;
        while ((c = pr.read()) != -1) {
            env.incRead();
            if (inescape) {
                switch (c) {
                    //@formatter:off
                    case 't': sb.append("\t"); break;
                    case 'b': sb.append("\b"); break;
                    case 'n': sb.append("\n"); break;
                    case 'r': sb.append("\r"); break;
                    case 'f': sb.append("\f"); break;
                    case '"': sb.append("\""); break;
                    case '\'': sb.append("'"); break;
                    case 'u': {
                        if (pr.read(unicode) != 4) {
                            throw lex.makeError("Unexpected end of file.");
                        }
                        for (char unum : unicode) {
                            if (! Character.isDigit(unum)) {
                                throw lex.makeError("Invalid unicode character '" + unum + "'.");
                            }
                        }
                        env.incRead(4);
                        // TODO There has to be a better way to do this
                        String numStr = String.format("%c%c%c%c", unicode[0], unicode[1], unicode[2], unicode[3]);
                        Integer num = Integer.parseInt(numStr);
                        char[] utf16 = Character.toChars(num);
                        sb.append(utf16);
                    }
                    //@formatter:on
                }
                inescape = false;
            } else {
                if (c == '\\') {
                    inescape = true;
                } else if (c == '\"') {
                	ParserResult res = new ParserResult(sb.toString(), TypeTag.STRING);
                    res = env.enrich(res);
                    return Optional.of(res);
                } else {
                    sb.append((char)c);
                    if (c == '\n') {
                        env.newline();
                    }
                }
            }
        }
        throw lex.makeError("Unexpected EOF while reading string literal.");
	}

}
