package pile.core.parse;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import pile.collection.PersistentList;
import pile.core.compiler.Compiler;

public class CompilerTest {

    @Test
    public void test() {
        PersistentList out = parse("(ns pile.core) (def abcd \"foo\")");
        Compiler compiler = new Compiler();
        compiler.evaluateTop(out);
        fail("TODO");
    }

    @Test
    public void testLet() {
        String s = """
                (ns pile.core)
                (let* [a "foo"]
                    (prnall a "bar"))
                """;
        PersistentList out = parse(s);
        Compiler compiler = new Compiler();
        compiler.evaluateTop(out);
        fail("TODO");
    }

    @Test
    public void testDef() {
        String s = """
                (ns something.else)
                (def dome (fn* [a b] a))
                (let* [foo (dome "foo" "bat")]
                    (prnall foo "bar"))
                """;
        PersistentList out = parse(s);
        Compiler compiler = new Compiler();
        compiler.evaluateTop(out);
        fail("TODO");
    }

    @Test
    public void testDefRefNonLocal() {
        String s = """
                (ns something.else)
                (def someval "1")
                (prnall someval)
                """;
        PersistentList out = parse(s);
        Compiler compiler = new Compiler();
        Compiler.evaluateTop(out);
        fail("TODO");
    }

    @Test
    public void testDefCallFn() {
        String s = """
                (ns something.else)
                (def dome (fn* [a] (prn a)))
                (dome "foo")
                """;
        PersistentList out = parse(s);
        Compiler compiler = new Compiler();
        compiler.evaluateTop(out);
        fail("TODO");
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
