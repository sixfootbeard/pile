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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import pile.collection.PersistentHashMap;
import pile.core.Symbol;

public class SymbolReader implements Reader {

    private static final Set<Character> ALLOWED_SPECIAL = Set.of('$', '&', '*', '+', '!', '-', '_', '\'', '?', '<', '>',
            '=', '/', '%', '#');

    public SymbolReader() {
    }

    @Override
    public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
        LexicalEnvironment lex = env.snapshot();
        StringBuilder sb = new StringBuilder();
        int i = pr.read();
        char c = (char) i;
        boolean first = true;

        while (c != -1 && ! Character.isWhitespace(c)
                && (Character.isAlphabetic(c) || 
                    Character.isDigit(c) || 
                    c == '.' ||
                    (! first && c == ':') ||
                    ALLOWED_SPECIAL.contains(c))) {
            env.incRead();
            sb.append((char) c);
            i = pr.read();
            c = (char) i;
            first = false;
        }
        if (sb.length() == 0) {
            return Optional.empty();
        }

        pr.unread(c);

        Symbol out;
        String part = sb.toString();
        if (part.startsWith("/")) {
            // /, /'
            out = new Symbol(part);
        } else {
            String[] parts = part.split("/");

            if (parts.length == 1) {
                out = new Symbol(sb.toString());
            } else if (parts.length == 2) {
                String symNs = parts[0];
                String sym = parts[1];

                out = new Symbol(symNs, sym, PersistentHashMap.EMPTY);
            } else {
                throw lex.makeError("Unexpected symbol part size: " + parts.length);
            }
        }

        out = lex.enrich(out);
        ParserResult result = new ParserResult(out, TypeTag.SYMBOL);
        if (out.getNamespace() == null) {
            String name = out.getName();
            Optional<Number> maybeNum = parseNumber(name, lex);
            if (maybeNum.isPresent()) {
                result = new ParserResult(maybeNum.get(), TypeTag.NUMBER);
            } else {            
                result = switch (name) {
                    case "true" -> new ParserResult(true, TypeTag.TRUE);
                    case "false" -> new ParserResult(false, TypeTag.FALSE);
                    case "nil" -> new ParserResult(null, TypeTag.NIL);
                    default -> result; // -_-
                };
            }
        }
        

        return Optional.of(lex.enrich(result));

    }
    
    private static Optional<Number> parseNumber(final String in, LexicalEnvironment start) {
        char first = in.charAt(0);
        // [-0-9]
        // not just "-"
        if (first == '-') {
            if (in.length() == 1 || ! isNumeric(in.charAt(1))) { 
                return Optional.empty();
            }
        }
        else if (!isNumeric(first)) {
            return Optional.empty();
        }
        Number out;
        char lastChar = in.charAt(in.length() - 1);
        if (lastChar == 'N') {
            out = new BigInteger(in.substring(0, in.length() - 1));
            return Optional.of(out);
        } else if (lastChar == 'b') {
            out = new BigDecimal(in.substring(0, in.length() - 1));
            return Optional.of(out);
        }
        boolean hasSuffix = ! isNumeric(lastChar);
        String test = in;
        if (hasSuffix) {
            test = in.substring(0, in.length() - 1);
        }
        
        if (test.contains(".")) {
            BigDecimal bd = new BigDecimal(test);
            if (hasSuffix) {
                switch (lastChar) {
                    case 'f': out = bd.floatValue(); break;
                    case 'd': out = bd.doubleValue(); break;
                    default: throw new ParseException("Unexpected suffix: " + lastChar, start);
                }
            } else {
                out = bd.doubleValue();
            }
        } else {
            BigInteger bi = new BigInteger(test);
            if (hasSuffix) {
                switch (lastChar) {
                    case 'L': out = bi.longValueExact(); break;
                    case 'f': out = bi.floatValue(); break;
                    case 'd': out = bi.doubleValue(); break;
                    default: throw new ParseException("Unexpected suffix: " + lastChar, start);
                }
            } else {
                out = bi.intValueExact();
            }
        }
        return Optional.of(out);
    }

    private static boolean isNumeric(char first) {
        return first >= 48 && first <= 57;
    }

}
