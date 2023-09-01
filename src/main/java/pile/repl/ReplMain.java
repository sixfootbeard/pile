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
package pile.repl;

import static pile.compiler.Helpers.*;

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.util.Optional;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.MacroEvaluated;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.Pile;
import pile.core.RuntimeRoot;
import pile.core.StandardLibraryLoader;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.exception.SourceLocation;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserResult;
import pile.core.parse.PileParser;

public class ReplMain {

    private static final String VERSION = "0.0.1-SNAPSHOT";

    public void run() throws Throwable {

        InputStreamReader isr = new InputStreamReader(System.in);
        PushbackReader pr = new PushbackReader(isr, 5);
        
        PCall pprint = Pile.getFunction("pile.pprint", "pretty-print");         

        Namespace replns = RuntimeRoot.defineOrGet("pile.repl");
        NativeDynamicBinding.NAMESPACE.set(replns);
        
        PrintStream out = System.out;
        PrintStream err = System.err;
        
        out.println("Pile "+ VERSION);
        out.println(" Help: (help 'function-name)");
        out.println(" Exit: Control+C, (quit), (exit)");
        out.println("");


        int captureCount = 0;
        while (! shouldExit()) {
            String prefix = NativeDynamicBinding.NAMESPACE.getValue() + "> "; 

            out.print(prefix);
            out.flush();

            try {
                // await input

                LexicalEnvironment lex = new LexicalEnvironment("<repl>");
                Optional<ParserResult> maybe = PileParser.parseSingle(lex, pr);
                if (maybe.isPresent()) {
                    char c = (char) pr.read();
                    if (c != '\n') {
                        pr.unread(c);
                    }
                }
                if (maybe.isEmpty()) {
                    continue;
                }
                Object evaluate = Compiler.evaluate(PersistentList.createArr(maybe.get().result()));

                evaluate = MacroEvaluated.unwrap(evaluate);
                
                String cname = "*" + captureCount;
                

                if (evaluate != null) {
                    out.print(cname + ": ");
                    pprint.invoke(evaluate);
                    out.print("\n");
                    out.flush();
                    Binding bind = Binding.ofValue(replns, evaluate);
                    replns.define(cname, bind);
                    ++captureCount;
                }

            } catch (Throwable t) {
                if (t instanceof SourceLocation sl) {
                    Optional<LexicalEnvironment> maybeLex = sl.getLocation();
                    if (maybeLex.isPresent()) {
                        LexicalEnvironment lex = maybeLex.get();
                        String padding = "~".repeat(prefix.length() + lex.getCharAtInLine());
                        // Error logs split up this indicator which makes it not as useful. 
                        // TODO Add back in
                        // err.println(padding + "^");
                    }
                }
                t.printStackTrace(System.err);
                err.flush();
            }

        }
    }
    
    private boolean shouldExit() {
        return (boolean) Pile.get("pile.repl", "exit-flag");
    }
  

    public static void main(String[] args) throws Throwable {
        ReplMain main = new ReplMain();
        main.run();
    }

}
