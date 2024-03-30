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

import pile.core.Keyword;
import pile.core.Symbol;

public class ParserConstants {
	
	public static final String PILE_CORE_NS = "pile.core";

	public static final Keyword QUOTE_KEY = Keyword.of(PILE_CORE_NS, "quote");
	public static final Keyword SYNTAX_QUOTE_KEY = Keyword.of(PILE_CORE_NS, "syntax-quote");
	public static final Keyword UNQUOTE_KEY = Keyword.of(PILE_CORE_NS, "unquote");
	public static final Keyword UNQUOTE_SPLICE_KEY = Keyword.of(PILE_CORE_NS, "unquote-splice");
	public static final Keyword SYMBOL_NAMESPACE = Keyword.of(PILE_CORE_NS, "symbol-namespace");
	public static final Keyword TAG_KEY = Keyword.of(PILE_CORE_NS, "typekey");
	public static final Keyword ANNO_TYPE_KEY = Keyword.of(PILE_CORE_NS, "annotated-type");
	
	public static final Keyword LINE_NUMBER_KEY = Keyword.of(PILE_CORE_NS, "line-number");
	public static final Keyword COLUMN_KEY = Keyword.of(PILE_CORE_NS, "column");
	public static final Keyword FILENAME_KEY = Keyword.of(PILE_CORE_NS, "filename");
	
	public static final Symbol VAR_SYM = new Symbol(PILE_CORE_NS, "var");
	
	public static final Symbol QUOTE_SYM = new Symbol(PILE_CORE_NS, "quote");
	public static final Symbol SYNTAX_QUOTE_SYM = new Symbol(PILE_CORE_NS, "syntax-quote");
	public static final Symbol UNQUOTE_SYM = new Symbol(PILE_CORE_NS, "unquote");
	public static final Symbol UNSPLICE_SYM = new Symbol(PILE_CORE_NS, "unquote-splice");
    
	public static final Symbol DEREF_SYM = new Symbol(PILE_CORE_NS, "deref");
    
    

	private ParserConstants() {
		// private
	}

}
