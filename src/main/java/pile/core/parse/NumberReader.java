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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Optional;

public class NumberReader implements Reader {

	public NumberReader() {
	}

	@Override
	public Optional<ParserResult> parse(LexicalEnvironment env, PushbackReader pr) throws IOException {
		LexicalEnvironment lex = env.snapshot();
		int read = pr.read();
		StringBuilder sb = new StringBuilder();
		if (read == '-' || (read >= 48 && read <= 57)) {
			sb.append((char)read);
			read = pr.read();
			while (! PileParser.isEndOfForm((char)read)) {
				sb.append((char)read);
				read = pr.read();
			}
			if (read != -1) {
				pr.unread(read);
			}
			Number num = parseNumber(env, sb.toString());
			ParserResult out = new ParserResult(num, TypeTag.NUMBER);
			return Optional.of(lex.enrich(out));				
		} else {
			pr.unread(sb.toString().toCharArray());
			return Optional.empty();
		}
		
	}
	
    private static Number parseNumber(LexicalEnvironment env, String in) {
        try {
            // TODO BigInt
            return NumberFormat.getInstance().parse(in);
        } catch (ParseException e) {
            throw env.makeError("Invalid number: '" + in + "'", e);
        }
    }

}
