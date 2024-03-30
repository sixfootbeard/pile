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
import static pile.nativebase.NativeCore.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.compiler.DeferredRegex;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Symbol;

@SuppressWarnings("rawtypes")
public class PileParser {

	private static final Symbol ANON_FN_SYM = new Symbol("anon-fn");
	
	public static final Keyword ADAPT_TYPE = Keyword.of("pile.core", "adapt-type");

	private static final Supplier<? extends Reader> CHAR_SUPPLIER = () -> new TokenEndingReader<>(TypeTag.CHAR, PileParser::parseCharacter, PileParser::isEndOfForm);

	private static final Supplier<? extends Reader> KEYWORD_SUPPLIER = () -> new TokenEndingReader<>(TypeTag.KEYWORD, PileParser::keywordMatcher, PileParser::isEndOfForm);

	private static <T> T unwrap(Optional<T> o) {
		return o.orElseThrow(() -> new RuntimeException("Unexpected empty parse result"));
	}

	static boolean isEndOfForm(char c) {
		return Character.isWhitespace(c) || c == ')' || c == ']' || c == '}';
	}

	private static Keyword keywordMatcher(String repr) {
		String[] parts = repr.split("/");
		String ns, name;
		if (parts.length == 1) {
			ns = null;
			name = parts[0];
		} else {
			ns = parts[0];
			name = parts[1];
		}
		return Keyword.of(ns, name);
	}

	private static final Map<String, Character> CHARACTER_LITERAL_NAMES = new HashMap<>();

	static {
		CHARACTER_LITERAL_NAMES.put("space", ' ');
	}

	private static Character parseCharacter(String in) {

		Character c = CHARACTER_LITERAL_NAMES.get(in);
		if (c == null) {
			throw new ParserException("Unexpected character literal name " + in + ", expected one of: "
					+ CHARACTER_LITERAL_NAMES.keySet().toString());
		}
		return c;

	}
	
	private static final DeferredDelegateReader TOP_READER = new DeferredDelegateReader();
	private static final DeferredDelegateReader TOP_DATA_READER = new DeferredDelegateReader();

	private static final Map<Character, Supplier<? extends Reader>> DISPATCH_MAP = new HashMap<>();
	private static final Map<Character, Supplier<? extends Reader>> DATA_DISPATCH_MAP = new HashMap<>();

	static {
		DISPATCH_MAP.put('{', 
				() -> new TransformerReader(
						new MultiFormReader(TypeTag.MAP, TOP_READER, new ExactStringReader("}", null)),
						PileParser::toSet)
				);
		DISPATCH_MAP.put('"', () -> {
			return (env, pr) -> {
	            LexicalEnvironment lex = env.snapshot();
	            
	            StringBuilder sb = new StringBuilder();
	            int c;
	            while ((c = pr.read()) != '"') {
	                env.incRead();
	                sb.append((char) c);
	            }

	            ParserResult result = new ParserResult(new DeferredRegex(sb.toString()), TypeTag.REGEX);
	            return Optional.of(lex.enrich(result));
			};
		});
		DISPATCH_MAP.put('(', () -> 
			new TransformerReader(new MultiFormReader(TypeTag.SEXP, TOP_READER, new ExactStringReader(")", null)),
					PileParser::toAnon)
		);
		DISPATCH_MAP.put('_', () -> {
			return (env, pr) -> {
				var _ = unwrap(TOP_READER.parse(env, pr));
				return Optional.of(new ParserResult(null, null));				
			};

		});
		
        DISPATCH_MAP.put('\'', () -> {
            return (env, pr) -> {
                var nextForm = TOP_READER.parse(env, pr);
                PersistentList<?> arr = PersistentList.createArr(nextForm.get().result(), ParserConstants.VAR_SYM);
                return Optional.of(new ParserResult(arr, TypeTag.SEXP));
            };
        });
		
		// Data
		DATA_DISPATCH_MAP.put('{', (Supplier<? extends Reader>) () -> new MultiFormReader(TypeTag.SET, TOP_DATA_READER, new ExactStringReader("}", null)));
		DATA_DISPATCH_MAP.put('_', (Supplier<? extends Reader>) () -> {
			return (env, pr) -> {
				var _ = unwrap(TOP_DATA_READER.parse(env, pr));
				return Optional.of(new ParserResult(null, null));
			};
		});
		
	}

	private static final Map<Character, Supplier<? extends Reader>> TOP_EXPR_MAP = new HashMap<>();
	private static final Map<Character, Supplier<? extends Reader>> TOP_DATA_EXPR_MAP = new HashMap<>();
	

    private static final ParserResult toAnon(ParserResult pr) {
		PersistentList result = (PersistentList) pr.result();
		PersistentList newVal = PersistentList.createArr(result, ANON_FN_SYM);
		return pr.withResult(newVal);
	}

	private static final ParserResult toVec(ParserResult pr) {
		return pr.withResult(PersistentVector.of((PersistentList)pr.result()));
	}
	
	private static final ParserResult toMap(ParserResult pr) {
		return pr.withResult(PersistentMap.fromIterable((PersistentList)pr.result()));
	}
	
	private static final ParserResult toSet(ParserResult pr) {
		return pr.withResult(PersistentSet.fromIterable((PersistentList)pr.result()));
	}
	
	private static Reader replace(Object val, Reader delegate) {
		return new TransformerReader(delegate, pr -> pr.withResult(val));
	}

	static {
		TOP_EXPR_MAP.put('"', StringFormReader::new);
		TOP_EXPR_MAP.put('(', () -> 
//		    new TransformerReader(
//		            new MultiFormReader(TypeTag.SEXP, TOP_READER, new ExactStringReader(")", null)),
//		            PileParser::firstSymbolInteropTransformer)
                new MultiFormReader(TypeTag.SEXP, TOP_READER, new ExactStringReader(")", null))
		    );
		TOP_EXPR_MAP.put('[',
				() -> new TransformerReader(
						new MultiFormReader(TypeTag.VEC, TOP_READER, new ExactStringReader("]", null)),
						PileParser::toVec));
		TOP_EXPR_MAP.put('{',
				() -> new TransformerReader(
						new MultiFormReader(TypeTag.MAP, TOP_READER, new ExactStringReader("}", null)),
						PileParser::toMap));

		TOP_EXPR_MAP.put('\\', CHAR_SUPPLIER);
		TOP_EXPR_MAP.put(':', KEYWORD_SUPPLIER);

		TOP_EXPR_MAP.put('#', () -> new CharTokenDispatchReader(DISPATCH_MAP));
		TOP_EXPR_MAP.put('^', () -> {
			return (env, pr) -> {
			    LexicalEnvironment lex = env.snapshot();
				var metaForm = unwrap(TOP_READER.parse(env, pr));
				skipWhitespace(env, pr);
				var toEnrich = unwrap(TOP_READER.parse(env, pr));
				
				if (toEnrich.result() instanceof Metadata meta) {
				    if (metaForm.tag() == TypeTag.KEYWORD) {
				        meta = meta.updateMeta(old -> old.assoc(metaForm.result(), true));
				    } else if (metaForm.tag() == TypeTag.SYMBOL) {
				        meta = meta.updateMeta(
				                (PersistentMap old) -> old.assoc(ParserConstants.ANNO_TYPE_KEY, metaForm.result()));
				    } else if (metaForm.tag() == TypeTag.MAP) {
				        PersistentMap result = (PersistentMap) metaForm.result();
				        // TODO Figure out what order makes sense here.
				        meta = meta.updateMeta((PersistentMap old) -> merge(old, result));
				    } else {
				        throw lex.makeError("Unexpected enrichment type: " + meta); 
				    }				    
				} else {
				    throw lex.makeError("Unexpected enrichment target: " + toEnrich.tag());    
				}

				return Optional.of(toEnrich.withResult(meta));
			};
		});
		
		// Macros
		TOP_EXPR_MAP.put('\'', () -> {
			return (env, pr) -> {
				var nextForm = TOP_READER.parse(env, pr);
				PersistentList<?> arr = PersistentList.createArr(nextForm.get().result(), ParserConstants.QUOTE_SYM);
				return Optional.of(new ParserResult(arr, TypeTag.SEXP));
			};
		});
		TOP_EXPR_MAP.put('`', () -> {
			return (env, pr) -> {
				var nextForm = TOP_READER.parse(env, pr);
				PersistentList<?> arr = PersistentList.createArr(nextForm.get().result(), ParserConstants.SYNTAX_QUOTE_SYM);
				return Optional.of(new ParserResult(arr, TypeTag.SEXP));
			};
		});
		TOP_EXPR_MAP.put('~', () -> {
			return (env, pr) -> {
				int read = pr.read();
				char c = (char) read;
				if (c == '@') {
				    var nextForm = TOP_READER.parse(env, pr);
				    PersistentList<?> arr = PersistentList.createArr(nextForm.get().result(), ParserConstants.UNSPLICE_SYM);
				    return Optional.of(new ParserResult(arr, TypeTag.SEXP));
				} else if (c == '#') {
				    // adapt-type
				    var maybe = TOP_READER.parse(env, pr);
				    var nextForm = maybe.orElseThrow(() -> error("Unexpected end of form"));
				    if (nextForm.result() instanceof Metadata meta) {
				        var updated = meta.updateMeta(old -> old.assoc(ADAPT_TYPE, true));
				        return Optional.of(new ParserResult(updated, nextForm.tag()));
				    }
				    throw new ParseException("Adapt type sugar target must be either Symbol or SExpr");
				} else {
				    pr.unread(read);
				    var nextForm = TOP_READER.parse(env, pr);
				    PersistentList<?> arr = PersistentList.createArr(nextForm.get().result(), ParserConstants.UNQUOTE_SYM);
				    return Optional.of(new ParserResult(arr, TypeTag.SEXP));
				}
			};
		});
		TOP_EXPR_MAP.put('@', () -> {
            return (env, pr) -> {
                var nextForm = TOP_READER.parse(env, pr);
                PersistentList<?> arr = PersistentList.createArr(nextForm.get().result(), ParserConstants.DEREF_SYM);
                return Optional.of(new ParserResult(arr, TypeTag.SEXP));
            };
        });

		Reader symbolReader = new SymbolReader();

		CharTokenDispatchReader dispatch = new CharTokenDispatchReader(TOP_EXPR_MAP);
		OneOfReader oneOf = new OneOfReader(dispatch, /*trueReader, falseReader, nilReader, numberReader,*/ symbolReader);
				
		TOP_READER.setDelegate(oneOf);
		
		// Data
		{
			TOP_DATA_EXPR_MAP.put('"', StringFormReader::new);
			TOP_DATA_EXPR_MAP.put('(', () -> new MultiFormReader(TypeTag.SEXP, TOP_DATA_READER, new ExactStringReader(")", null)));
			TOP_DATA_EXPR_MAP.put('[',
					() -> new TransformerReader(
							new MultiFormReader(TypeTag.VEC, TOP_DATA_READER, new ExactStringReader("]", null)),
							PileParser::toVec));
			TOP_DATA_EXPR_MAP.put('{',
					() -> new TransformerReader(
							new MultiFormReader(TypeTag.MAP, TOP_DATA_READER, new ExactStringReader("}", null)),
							PileParser::toMap));
			TOP_DATA_EXPR_MAP.put('\\', CHAR_SUPPLIER);
			TOP_DATA_EXPR_MAP.put(':', KEYWORD_SUPPLIER);
			TOP_DATA_EXPR_MAP.put('#', () -> new CharTokenDispatchReader(DATA_DISPATCH_MAP));
	
			CharTokenDispatchReader dataDispatch = new CharTokenDispatchReader(TOP_DATA_EXPR_MAP);
			OneOfReader dataOneOf = new OneOfReader(dataDispatch, /*trueReader, falseReader, nilReader, numberReader,*/ symbolReader);
			TOP_DATA_READER.setDelegate(dataOneOf);
		}
	}


	public static void skipWhitespace(LexicalEnvironment env, PushbackReader pr) throws IOException {
        for (;;) {
            int read = pr.read();
            switch (read) {
            	case ';':
            		env.incRead();
            		while (read != '\n') {
            			read = pr.read();
            			if (read == -1) return;
            			env.incRead();
            		}
            		env.newline();
            		break;
                case '\n':
                    env.newline();
                    break;
                case ',':
                case '\t':
                case ' ':
                    env.incRead();
                    break;
                default:
                    pr.unread(read);
                case -1:
                    return;
            }
        }
    }

	public PileParser() {}
	
	public static ParserResult parseSingle(String s) {
	    LexicalEnvironment env = new LexicalEnvironment("<string>");
	    StringReader sr = new StringReader(s);
	    return parseSingle(env, sr).orElseThrow(() -> env.makeError("Parse error"));
	}
	
	public static Optional<ParserResult> parseSingleOpt(String s) {
	    LexicalEnvironment env = new LexicalEnvironment("<string>");
	    return parseSingle(env, new StringReader(s));
    }
    
    
    public static Optional<ParserResult> parseSingle(java.io.Reader r) {
        LexicalEnvironment env = new LexicalEnvironment("<string>");
        return parseSingle(env, r);
    }
	
    public static Optional<ParserResult> parseSingle(LexicalEnvironment env, java.io.Reader r) {
        try {
            PushbackReader pr = r instanceof PushbackReader p ? 
                                    p : new  PushbackReader(r, 5);
            return TOP_READER.parse(env, pr);
        } catch (IOException e) {
            throw new ParserException(e);
        }
    }
	
	public static Object parseData(java.io.Reader sr) {
		try {
			var packed = TOP_DATA_READER.parse(new LexicalEnvironment("<reader>"), new PushbackReader(sr, 5));
			return packed.get().result();
		} catch (IOException e) {
			throw new ParserException(e);
		}
	}
	
	public static PersistentList parse(java.io.Reader r, String filename) throws IOException {
		try (PushbackReader pr = new PushbackReader(r, 5)) {
			LexicalEnvironment env = new LexicalEnvironment(filename);
			return parse(pr, filename, env);
		}
	}
	
	public static PersistentList parse(InputStream is, String filename) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                PushbackReader pr = new PushbackReader(isr, 5)) {
        	LexicalEnvironment env = new LexicalEnvironment(filename);        
            return parse(pr, filename, env);
        }
    }

	private static PersistentList parse(PushbackReader pr, String filename, LexicalEnvironment env) throws IOException {
		MultiFormReader fileReader = new MultiFormReader(null, TOP_READER, (environ, reader) -> {
			int read = reader.read();
			if (read == -1) {
				return Optional.of(new ParserResult(null, null));
			} else {
				reader.unread(read);
				return Optional.empty();
			}
		});
		skipWhitespace(env, pr);
		var top = fileReader.parse(env, pr);
		return (PersistentList) top.get().result();
	}

}
