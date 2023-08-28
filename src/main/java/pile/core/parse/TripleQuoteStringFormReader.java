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
import java.util.stream.Collectors;

import pile.compiler.Helpers;

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
        
        boolean done = false;
        
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        
        outer:
        while (! done) {
            int read = pr.read();
            switch (read) {
                case -1: throw lex.makeError("Unexpected EOF while reading string literal.");
                case '\n':
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                    env.newline();
                    break;
                case '"': 
                    pr.unread('"');
                    if (readTripleQuote(pr)) {
                        String currentStr = currentLine.toString();
                        if (currentStr.length() > 0 && prefixSpaceCount(currentStr) >= 0) {
                            lines.add(currentLine.toString());
                        }
                        env.incRead(3);
                        break outer;
                    }
                    // fall through                 
                default:
                    currentLine.append((char)read);
                    env.incRead();
                    break;
            }
        }
        if (lines.size() == 0) {
            return Optional.of(new ParserResult("", TypeTag.STRING));
        }
        var minPrefixOpt = lines.stream()
                             .mapToInt(this::prefixSpaceCount)
                             .filter(i -> i > 0)
                             .min();
                             
        if (minPrefixOpt.isEmpty()) {
            // we have lines but they are all empty. 
            // would look visually like:
            // """
            // 
            // """
            
            // Clearly different then just without any lines which should return empty:
            // """
            // """
            return Optional.of(new ParserResult("\n".repeat(lines.size()), TypeTag.STRING));
        }
                             
        String outputString = lines.stream().map(s -> removePrefix(s, minPrefixOpt.getAsInt())).collect(Collectors.joining("\n"));
        
        ParserResult result = new ParserResult(outputString, TypeTag.STRING);
        return Optional.of(result);
    }
    
    /**
     * 
     * @param s
     * @param prefixCount
     * @return The substring starting at prefixCount or empty string if there are not enough characters.
     */
    private String removePrefix(String s, int prefixCount) {
        if (s.length() <= prefixCount) {
            return "";
        } else {
            return s.substring(prefixCount);
        }
    }
    
    /**
     * 
     * @param s 
     * @return The number of leading spaces, or -1 if all leading characters are spaces (including empty string).
     */
    private int prefixSpaceCount(String s) {
        for (int idx = 0; idx < s.length(); ++idx) {
            if (s.charAt(idx) != ' ') {
                return idx;
            }
        }
        return -1;
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
