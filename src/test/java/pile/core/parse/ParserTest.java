package pile.core.parse;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import pile.collection.PersistentList;

public class ParserTest {

    @Test
    public void testOpenClose() {
        PersistentList out = parse("()");
    }
    
    @Test
    public void testPlus() {
        PersistentList out = parse("(ns pile.core) (* 4 5)");
        printParsed(out);
    }
    
    @Test
    public void testPrint() {
        PersistentList out = parse("(ns pile.core) (testprn \"foo\")");
        printParsed(out);
    }

    private void printParsed(PersistentList out) {
        for (Object o : out) {
            PersistentList inner = (PersistentList) o;
            System.out.print(inner.meta().get(Parser.TAG_KEY) + "=");
            System.out.println(o);
        }
    }

    private PersistentList parse(String string) {
        Parser p = new Parser();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8))) {
            return p.parse(bais, "<stdin>");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
