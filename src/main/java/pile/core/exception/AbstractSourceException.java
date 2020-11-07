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
package pile.core.exception;

import java.util.Optional;

import pile.core.Metadata;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;

public abstract class AbstractSourceException extends PileException implements SourceLocation {

    private Optional<LexicalEnvironment> lex = Optional.empty();

    private String msg;

    /**
     * 
     */
    private static final long serialVersionUID = -6584161637237871021L;

    public AbstractSourceException() {
    }

    public AbstractSourceException(String message) {
        super(message);
    }

    public AbstractSourceException(Throwable cause) {
        super(cause);
    }

    public AbstractSourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public AbstractSourceException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public AbstractSourceException(String msg, Optional<LexicalEnvironment> maybeLex) {
        super(makeMessage(msg, maybeLex));
        this.lex = maybeLex;
    }
    
    public AbstractSourceException(String msg, Optional<LexicalEnvironment> maybeLex, Throwable t) {
        super(makeMessage(msg, maybeLex), t);
        this.lex = maybeLex;
    }
    
    public Optional<LexicalEnvironment> getLex() {
        return lex;
    }
    
    @Override
    public Optional<LexicalEnvironment> getLocation() {
        return getLex();
    }

    public static String makeMessage(String msg, Optional<LexicalEnvironment> maybeLex) {
        String base = maybeLex
                .map(lex -> String.format("[f:%s/l:%d/c:%d] ", lex.getSource(), lex.getLineAt(), lex.getCharAtInLine()))
                .orElse("");
        return msg + " " + base;
    }

}
