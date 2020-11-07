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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;

import org.junit.Before;
import org.junit.Test;

public class TripleQuoteStringFormReaderTest {

    LexicalEnvironment env;
    TripleQuoteStringFormReader reader;

    @Before
    public void setup() {
        env = new LexicalEnvironment();
        reader = new TripleQuoteStringFormReader();
    }

    @Test
    public void test() throws IOException {
        read("\"\"\"\n\"\"\"");
        assertLex(2, 3);
    }
    
    @Test
    public void testWhitespace() throws IOException {
        read("\"\"\"\n   \"\"\"");
        assertLex(2, 6);
    }
    
    @Test
    public void testAbcd() throws IOException {
        read("\"\"\"\n   abcd\"\"\"");
        assertLex(2, 10);
    }
    
    @Test
    public void testAbcdMultiLine() throws IOException {
        read("\"\"\"\n\n\n   abcd\"\"\"");
        assertLex(4, 10);
    }
    
    private void assertLex(int row, int column) {
        assertEquals("Row was wrong", row, env.getLineAt());
        assertEquals("Character at in line is wrong", column, env.getCharAtInLine());        
    }

    private void read(String s) throws IOException {
        PushbackReader pr = new PushbackReader(new StringReader(s));
        reader.parse(env, pr);
    }

}
