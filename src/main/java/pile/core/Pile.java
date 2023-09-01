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

import static java.util.Objects.*;

import pile.core.binding.Binding;

/**
 * Functions for external languages to retrieve bindings or functions.
 *
 */
public class Pile {

    private Pile() {
    }

    public static PCall getFunction(String ns, String fname) {
        Object value = get(ns, fname);
        if (value instanceof PCall pcall) {
            return pcall;
        } else {
            System.out.println(ns + "/" + fname + " is not callable");
            throw new IllegalArgumentException();
        }
    }

    /**
     * Get the current value of the provided ns/name
     * 
     * @param ns
     * @param fname
     * @return The current value of the ns/name
     * @throws NullPointerException If the namespace or name don't exist.
     */
    public static Object get(String ns, String fname) {
        Namespace namespace = RuntimeRoot.defineOrGet(ns);
        requireNonNull(namespace, "Namespace doesn't exist");
        Binding bind = Namespace.getIn(namespace, fname);
        requireNonNull(namespace, ns + "/" + fname + " doesn't exist");
        return bind.getValue();
    }

}
