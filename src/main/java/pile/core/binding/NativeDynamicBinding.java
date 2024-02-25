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
package pile.core.binding;

import java.io.InputStream;
import java.io.PrintStream;
import java.lang.invoke.SwitchPoint;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.core.Keyword;
import pile.core.Namespace;
import pile.core.PileProperties;
import pile.core.Symbol;
import pile.core.indy.CompilerFlags;
import pile.core.indy.PileMethodLinker;
import pile.core.log.LogLevel;

/**
 * A {@link ThreadLocal}, essentially.
 *
 * @param <T>
 */
public class NativeDynamicBinding<T> extends ThreadLocalBinding<T> {

    private static final String DEFAULT_LOG_LEVEL_STR = "INFO";

    // faux enum so we can have generics
    
    //@formatter:off
    public static NativeDynamicBinding<Namespace> NAMESPACE = new NativeDynamicBinding<>("*ns*"); 
    public static NativeDynamicBinding<InputStream> STANDARD_IN = new NativeDynamicBinding<>("*in*", System.in); 
    public static NativeDynamicBinding<PrintStream> STANDARD_OUT = new NativeDynamicBinding<>("*out*", System.out);
    public static NativeDynamicBinding<PrintStream> STANDARD_ERR = new NativeDynamicBinding<>("*err*", System.err);
    public static NativeDynamicBinding<CompilerFlags> COMPLILER_FLAGS = new NativeDynamicBinding<>("*compiler-flags*", new CompilerFlags());
    public static NativeDynamicBinding<PersistentList<Symbol>> CURRENT_FN_SYM = new NativeDynamicBinding<>("*compiling-sym*", PersistentList.EMPTY); 
    // Only emit boxed numbers for literals
    public static NativeDynamicBinding<Boolean> BOXED_NUMBERS = new NativeDynamicBinding<>("*boxed-numbers*", false);    
    // Try to compile errors into runtime exceptions (usually only on during development)
    public static NativeDynamicBinding<Boolean> DEFER_ERRORS = new NativeDynamicBinding<>("*defer-errors*", false);
    public static NativeDynamicBinding<String> COMPILE_FILENAME = new NativeDynamicBinding<>("*filename*"); 
    public static NativeDynamicBinding<LogLevel> ROOT_LOG_LEVEL = new NativeDynamicBinding<>("*log-level*",
            LogLevel.valueOf((String)PileProperties.PROPERTIES.get(Keyword.of(null, "log-level"), DEFAULT_LOG_LEVEL_STR)));
    //@formatter:on

    public static NativeDynamicBinding[] values() {
        return new NativeDynamicBinding[] { NAMESPACE, STANDARD_IN, STANDARD_OUT, STANDARD_ERR, COMPLILER_FLAGS,
                BOXED_NUMBERS, DEFER_ERRORS, COMPILE_FILENAME, ROOT_LOG_LEVEL };
    }

    public NativeDynamicBinding(String name) {
        this(name, null);
    }

    public NativeDynamicBinding(String name, T initial) {
        this(name, true, initial);
    }
    
    public NativeDynamicBinding(String name, boolean isFinal, T initial) {
        this("pile.core", name, isFinal, initial);
    }
    
    public NativeDynamicBinding(String ns, String name, boolean isFinal, T initial) {
        super(ns, name, initial, PersistentMap.createArr(PileMethodLinker.FINAL_KEY, isFinal, Binding.BINDING_TYPE_KEY, BindingType.DYNAMIC),
                new SwitchPoint());
    }
    
}
