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

import java.util.Optional;

import pile.collection.PersistentMap;
import pile.core.Metadata;
import pile.core.exception.PileSyntaxErrorException;

public class LexicalEnvironment {
    private int charAtInLine = 0;
    private int lineAt = 1;
    private String source = "<unknown>";
    
    public LexicalEnvironment() {
	}
    
    public LexicalEnvironment(String source) {
        this.source = source;
    }

    public LexicalEnvironment(String source, int charAtInLine, int lineAt) {
		super();
		this.charAtInLine = charAtInLine;
		this.lineAt = lineAt;
		this.source = source;
	}

	void incRead(int size) {
        charAtInLine += size;
    }

    void incRead() {
        incRead(1);
    }
    
    

    public int getCharAtInLine() {
        return charAtInLine;
    }

    public int getLineAt() {
        return lineAt;
    }

    public String getSource() {
        return source;
    }

    void newline() {
        lineAt++;
        charAtInLine = 0;
    }

    @Override
    public String toString() {
        return "LexicalEnvironment [source=" + source + ", charAtInLine=" + charAtInLine + ", lineAt=" + lineAt + "]";
    }

    public LexicalEnvironment snapshot() {
		return new LexicalEnvironment(source, charAtInLine, lineAt);
	}
	
	public PersistentMap toMap() {
        return PersistentMap.createArr(
                ParserConstants.LINE_NUMBER_KEY, lineAt, 
                ParserConstants.COLUMN_KEY, charAtInLine,
                ParserConstants.FILENAME_KEY, source);
	}
	
	public <T extends Metadata> T enrich(T meta) {
	    // TODO use above
		return (T) meta.updateMeta(old -> 
		    old.assoc(ParserConstants.LINE_NUMBER_KEY, lineAt)
		    .assoc(ParserConstants.COLUMN_KEY, charAtInLine)
		    .assoc(ParserConstants.FILENAME_KEY, source)
		       );
	}

    public ParseException makeError(String string) {
        throw new ParseException(string, this);
    }

    public ParseException makeError(String string, Throwable e) {
        throw new ParseException(string, this, e);
    }
    
//    public static Optional<LexicalEnvironment> extract(Object base) {
//        if (base instanceof Metadata meta) {
//            return fromMap(meta.meta());
//        }
//        return Optional.empty(); 
//    }
    
    public static Optional<LexicalEnvironment> extract(Object... bases) {
        for (var base : bases) {
            if (base instanceof Metadata meta) {
                return fromMap(meta.meta());
            }
        }
        return Optional.empty(); 
    }
    
    public static Optional<LexicalEnvironment> fromMap(PersistentMap base) {
        String maybeFilename = (String) ParserConstants.FILENAME_KEY.call(base);
        Object maybeLineNumber = ParserConstants.LINE_NUMBER_KEY.call(base);
        Object maybeColumn = ParserConstants.COLUMN_KEY.call(base);
        if (maybeFilename != null && maybeLineNumber != null && maybeColumn != null) {
            return Optional.of(new LexicalEnvironment(maybeFilename, (int) maybeColumn, (int) maybeLineNumber));
        }
        return Optional.empty();
    }
    
}