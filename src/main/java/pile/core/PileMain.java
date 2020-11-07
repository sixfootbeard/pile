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
package pile.core;

import static pile.nativebase.NativeCore.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import pile.collection.PersistentHashMap;
import pile.core.binding.Binding;
import pile.core.parse.PileParser;

/**
 * Main entry point for the language. Expects a single argument of the namespace
 * to load first. Expects a function named '-main' in that namespace and it
 * calls that function. The function receives as arguments the rest of the
 * command line arguments.
 *
 */
public class PileMain {

    public static void main(String[] args) throws Throwable {

        Namespace namespace = RuntimeRoot.defineOrGet(args[0]);
        if (namespace == null) {
            System.err.println("No namespace found: " + args[0]);
            System.exit(1);
        }
        Binding local = namespace.getLocal("-main");
        Object deref = local.getValue();
        if (deref instanceof PCall method) {
            String[] argTail = Arrays.copyOfRange(args, 1, args.length);
            method.applyInvoke(seq(argTail));
        } else {
            throw new IllegalArgumentException("No method at " + namespace.getName() + "/-main");
        }
    }

}
