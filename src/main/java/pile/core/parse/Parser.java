package pile.core.parse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import pile.collection.PersistentList;
import pile.collection.PersistentHashMap;
import pile.core.Cons;
import pile.core.Keyword;
import pile.core.Metadata;

public class Parser {

    public static final Keyword TAG_KEY = Keyword.of("pile.core", "typekey");

    public enum TypeTag {
        SET, REGEX, VAR, ANON_FN, FORM_IGNORE, STRING, SEXP, VEC, MAP, NUMBER, CHAR, TRUE, FALSE, KEYWORD, SYMBOL;
    }

    public static class Literal implements Metadata {

        private final Object val;
        private final PersistentHashMap meta;
        
        public Literal(PersistentHashMap meta, Object val) {
            super();
            this.val = val;
            this.meta = meta;
        }

        public Literal(TypeTag tag, Object val) {
            super();
            this.val = val;
            this.meta = new PersistentHashMap<>().assoc(TAG_KEY, tag);
        }

        public Object getVal() {
            return val;
        }

        @Override
        public PersistentHashMap meta() {
            return meta;
        }

        @Override
        public Literal withMeta(PersistentHashMap newMeta) {
            return new Literal(newMeta, val);
        }

        @Override
        public Literal updateMeta(Function<PersistentHashMap, PersistentHashMap> update) {
            return new Literal(update.apply(meta), val);
        }
        
        @Override
        public String toString() {
            return String.format("Literal[val=" + val + ", meta=" + meta + "]");
        }

    }

    public static class LexicalEnvironment {
        private int charAtInLine = 0;
        private int lineAt = 0;

        private void incRead(int size) {
            charAtInLine += size;
        }

        private void incRead() {
            incRead(1);
        }

        private void newline() {
            lineAt++;
            charAtInLine = 0;
        }
    }

    public PersistentList parse(InputStream is, String name) throws IOException {

        LexicalEnvironment env = new LexicalEnvironment();
        
        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                PushbackReader pr = new PushbackReader(isr, 5)) {
            MultiFormReader mfr = new MultiFormReader(TypeTag.SEXP, true);
            return mfr.parse(env, pr, name);
        }
    }

    private void expect(PushbackReader pr, LexicalEnvironment env, char s) throws IOException {
        if (pr.read() != s) {
            throw new RuntimeException("Didn't find expected character");
        }
        env.incRead();
    }

    private static void skipWhitespace(LexicalEnvironment env, PushbackReader pr) throws IOException {
        for (;;) {
            int read = pr.read();
            switch (read) {
                case '\n':
                    env.newline();
                    break;
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

    public static void error(String string) {
        throw new RuntimeException(string);
    }

    private static Function<String, String> exactMatcher(String expected, String type) {
        return (in) -> {
            if (!(expected.equals(in))) {
                throw new IllegalArgumentException(
                        "Unexpected type=" + type + " value, expected " + expected + ", found " + in);
            }
            return in;
        };
    }

    private static Number parseNumber(String in) {
        try {
            // TODO BigInt
            return NumberFormat.getInstance().parse(in);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid number", e);
        }
    }

    private static final Map<String, Character> CHARACTER_LITERAL_NAMES = new HashMap<>();

    static {
        CHARACTER_LITERAL_NAMES.put("space", ' ');
    }

    private static Character parseCharacter(String in) {

        Character c = CHARACTER_LITERAL_NAMES.get(in);
        if (c == null) {
            throw new IllegalArgumentException("Unexpected character literal name " + in + ", expected one of: "
                    + CHARACTER_LITERAL_NAMES.keySet().toString());
        }
        return c;

    }
    
    private static boolean isEndOfForm(char c) {
        return Character.isWhitespace(c) || c == ')';
    }

    interface Reader {
        Metadata parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException;
    }

    private static final Map<Character, Supplier<? extends Reader>> DISPATCH_MAP = new HashMap<>();
 
    static {
        DISPATCH_MAP.put('{', () -> new BoundedMultiReader(TypeTag.SET, '}'));
        DISPATCH_MAP.put('"', RegexReader::new);
        DISPATCH_MAP.put('\'', VarReader::new);
        DISPATCH_MAP.put('(', () -> new BoundedMultiReader(TypeTag.ANON_FN, ')'));
        DISPATCH_MAP.put('_', FormIgnoringReader::new);
    }

    private static final Map<Character, Supplier<? extends Reader>> ALL_EXP_MAP = new HashMap<>();

    static {
        ALL_EXP_MAP.put('"', StringReader::new);
        ALL_EXP_MAP.put('(', () -> new BoundedMultiReader(TypeTag.SEXP, ')'));
        ALL_EXP_MAP.put('[', () -> new BoundedMultiReader(TypeTag.VEC, ']'));
        ALL_EXP_MAP.put('{', () -> new BoundedMultiReader(TypeTag.MAP, '}'));
        ALL_EXP_MAP.put('#', () -> {
            return (env, pr, file) -> {
                int c = pr.read();
                env.incRead();
                throwEOF(c);
                Supplier<? extends Reader> req = getReq(DISPATCH_MAP, (char) c);
                return req.get().parse(env, pr, file);
            };
        });
        for (int i = 48; i < 58; ++i) {
            ALL_EXP_MAP.put((char)i,
                    () -> new TokenEndingReader<>(TypeTag.NUMBER, Parser::parseNumber, Parser::isEndOfForm));
        }
        ALL_EXP_MAP.put('\\', () -> new TokenEndingReader<>(TypeTag.CHAR, Parser::parseCharacter, Parser::isEndOfForm));
//        ALL_EXP_MAP.put('t',
//                () -> new TokenEndingReader<>(TypeTag.TRUE, Parser.exactMatcher("true", "TRUE"), Parser::isEndOfForm));
//        ALL_EXP_MAP.put('f',
//                () -> new TokenEndingReader<>(TypeTag.FALSE, Parser.exactMatcher("false", "FALSE"), Parser::isEndOfForm));
        ALL_EXP_MAP.put(':', () -> new TokenEndingReader<>(TypeTag.KEYWORD, Parser.keywordMatcher(), Parser::isEndOfForm));
    }

    private static class SymbolOrTrueOrFalseReader implements Reader {

        Set<Character> allowedSpecial = new HashSet<>(Arrays.asList('*', '+', '!', '-', '_', '\'', '?', '<', '>', '='));

        public SymbolOrTrueOrFalseReader() {
        }

        @Override
        public Literal parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
            StringBuilder sb = new StringBuilder();
            int i = pr.read();
            char c = (char) i;
            if (c == 't') {
                if (ensure(pr, 'r', 'u', 'e')) {
                    return new Literal(TypeTag.TRUE, true);
                }
//                pr.unread('t');
            } else if (c == 'f') {
                if (ensure(pr, 'a', 'l', 's', 'e')) {
                    return new Literal(TypeTag.FALSE, false);
                }
//                pr.unread('f');
            }
            while (c != -1 && !Character.isWhitespace(c)
                    && (Character.isAlphabetic(c) || Character.isDigit(c) || 
                            c == '.' || allowedSpecial.contains(c))) {
                env.incRead();
                sb.append((char) c); // TODO type casting?
                i = pr.read();
                c = (char) i;
            }
            pr.unread(c);
            return new Literal(TypeTag.SYMBOL, sb.toString());
        }

        private boolean ensure(PushbackReader pr, char... chars) throws IOException {
            for (int i = 0; i < chars.length; ++i) {
                int r = pr.read();
                if (r == -1) {
                    return false;
                }
                if (chars[i] != (char) r) {
                    pr.unread(r);
                    for (int rb = i - 1; rb >= 0; --rb) {
                        pr.unread(chars[rb]);
                    }
                    return false;
                }
            }
            
            return true;
        }
    }

    private static class MultiFormReader implements Reader {

        private final Map<Character, Supplier<? extends Reader>> readerCons;
        private final TypeTag tag;
        private final boolean ignoreFirst;
        
        public MultiFormReader(TypeTag tag, boolean ignoreFirst) {
            this(tag, ignoreFirst, ALL_EXP_MAP);
        }

        public MultiFormReader(TypeTag tag) {
            this(tag, false);
        }
        
        public MultiFormReader(TypeTag tag, boolean ignoreFirst, Map<Character, Supplier<? extends Reader>> readerCons) {
            super();
            this.tag = tag;
            this.readerCons = readerCons;
            this.ignoreFirst = ignoreFirst;
        }

        @Override
        public PersistentList parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
            List<Object> list = new ArrayList<>();
            if (! ignoreFirst) {
                pr.read(); env.incRead();
            }
            for (;;) {

                int read = pr.read();
                if (read == -1) {
                    checkEOF(true);
                    break;
                }
                env.incRead();

                char c = (char) read;
                if (isEnd(c)) {
                    break;
                }
                pr.unread(read);
                Supplier<? extends Reader> supplier = readerCons.get(c);
                final Reader reader;
                if (supplier == null) {
                    // Fallback                    
                    if (!Character.isDigit(c)) {
                        reader = new SymbolOrTrueOrFalseReader();
                    } else {
                        throw new RuntimeException(
                                "Cannot figure out how to parse starting with character '" + c + '"');
                    }
                } else {
                    reader = supplier.get();
                }
                Object constructed = reader.parse(env, pr, file);
                if (constructed != null) {
                    // TODO conditionally add when not null seems like it'll mask some bugs...
                    list.add(constructed);
                }
                skipWhitespace(env, pr);
            }
            
            // (+ 1 2)
            // (2, (1, (+, null)))
            PersistentList<Object> out = PersistentList.fromList(list);

            out = out.updateMeta((PersistentHashMap old) -> old.assoc(TAG_KEY, tag));

            return out;
        }

        protected boolean isEnd(char c) {
            // EOF only for top level parser
            return false;
        }

        protected void checkEOF(boolean wasEOF) {
            // pass
        }

    }

    private static class TokenEndingReader<T> implements Reader {

        private final Function<String, T> fn;
        private final TypeTag tag;
        private final Predicate<Character> end;

        public TokenEndingReader(TypeTag tag, Function<String, T> fn, Predicate<Character> doEnd) {
            this.tag = tag;
            this.fn = fn;
            this.end = doEnd;
        }

        @Override
        public Literal parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
           
            StringBuilder sb = new StringBuilder();
            int c = pr.read();
            while (c != -1 && ! end.test((char)c)) {
                env.incRead();
                sb.append((char) c); // TODO type casting?
                c = pr.read();
            }
            if (c != -1) {
                pr.unread(c);
            }
            return new Literal(tag, fn.apply(sb.toString()));
        }

    }

    private static class BoundedMultiReader extends MultiFormReader {

        private final char end;

        public BoundedMultiReader(TypeTag typeTag, char end) {
            super(typeTag);
            this.end = end;
        }

        @Override
        protected boolean isEnd(char c) {
            return c == end;
        }

        @Override
        protected void checkEOF(boolean wasEOF) {
            if (wasEOF) {
                throw new RuntimeException("Unexpected EOF");
            }
        }

    }

    private static class StringReader implements Reader {

        private char[] unicode = new char[4];

        public Literal parse(LexicalEnvironment env, PushbackReader pr, String name) throws IOException {
            // Discard dq
            pr.read(); env.incRead();
            
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
                                error("Unexpected end of file");
                            }
                            for (char unum : unicode) {
                                if (! Character.isDigit(unum)) {
                                    error("Invalid unicode character '" + unum + "'");
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
                        return new Literal(TypeTag.STRING, sb.toString());
                    } else {
                        sb.append((char)c);
                    }
                }

            }
            throw new RuntimeException("Unexpected EOF");
        }

    }

    private static class CommentReader implements Reader {

        @Override
        public Metadata parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
            int c;
            while ((c = pr.read()) != '\n') {
                env.incRead();
            }
            return null;
        }
    }

    private static class FormIgnoringReader implements Reader {

        @Override
        public Metadata parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
            MultiFormReader m = new MultiFormReader(/* form ignoring tag */null);
            // Ignore output
            m.parse(env, pr, file);
            return null;
        }
    }

    private static class VarReader implements Reader {

        @Override
        public Metadata parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
            throw new RuntimeException("Not implemented");
        }

    }

    private static class RegexReader implements Reader {

        @Override
        public Literal parse(LexicalEnvironment env, PushbackReader pr, String file) throws IOException {
            // Discard
            pr.read(); env.incRead();
            
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = pr.read()) != '\n') {
                env.incRead();
                sb.append((char) c); // TODO type?
            }

            return new Literal(TypeTag.REGEX, Pattern.compile(sb.toString()));
        }

    }

    private static Function<String, Keyword> keywordMatcher() {
        return (repr) -> {
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
        };
    }

    private static <K, V> V getReq(Map<K, V> dispatchMap, K c) {
        if (!dispatchMap.containsKey(c)) {
            throw new IllegalArgumentException("Expected key '" + c + "' to exist");
        }
        return dispatchMap.get(c);

    }

    private static void throwEOF(int c) {
        if (c == -1) {
            throw new IllegalStateException("Unexpected EOF");
        }

    }
}
