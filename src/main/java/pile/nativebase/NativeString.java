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
package pile.nativebase;

import static pile.nativebase.NativeCore.*;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import pile.collection.PersistentList;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;

public class NativeString {

    private NativeString() {}
    
    @NativeBinding("*builder*")
    public static NativeDynamicBinding<PersistentList<StringBuilder>> BUILDER = new NativeDynamicBinding<>("pile.string",
            "*builder*", true, null);
            
    @PileDoc("Creates and pushes a new thread local string builder. It is implicitly referenced by (append \"string\") and will be popped by calling (build).")
    public static void new_builder() {
        StringBuilder sb = new StringBuilder();
        PersistentList<StringBuilder> newList = (PersistentList<StringBuilder>) conj(BUILDER.getValue(), sb);
        BUILDER.set(newList);
    }       
            
    @PileDoc("Appends to the implicit string builder. Throws NPE if no local string builder exists")
    public static void append(Object o) {
        StringBuilder head = BUILDER.getValue().head();
        append(head, o);
    }
    
    @PileDoc("Appends to the provided string builder.")
    public static void append(StringBuilder sb, Object o) {
        sb.append(NativeCore.str(o));
    }
    
    @PileDoc("Returns the string built by the stringbuilder")
    public static String build(StringBuilder sb) {
        return sb.toString();
    }
    
    @PileDoc("Pops the current stringbuilder and returns the value it was building")
    public static String build() {
        PersistentList<StringBuilder> pl = BUILDER.getValue();
        StringBuilder head = pl.head();
        BUILDER.set(pl.pop());
        return build(head);
    }

    @PileDoc("Returns the number of code points in the unicode string")
    public static int length(CharSequence cs) {
        String s = cs.toString();
        return s.codePointCount(0, s.length());
    }
    
    @PileDoc("Returns the substring starting from the provided code point, optionally up to the provided code point.")
    @Precedence(0)
    public static String substr(CharSequence cs, int from) {
        var s = cs.toString();
        int idx = s.offsetByCodePoints(0, from);
        return s.substring(idx, s.length());                
    }
    
    @Precedence(1)
    public static String substr(CharSequence cs, int from, int to) {
        var s = cs.toString();
        int idx = s.offsetByCodePoints(0, from);
        int end = s.offsetByCodePoints(0, to);
        return s.substring(idx, end);                
    }
    
    @PileDoc("Returns the true if the provided string is blank. Throws NPE if the provided string is null.")
    @RenamedMethod("blank?")
    public static boolean isBlank(CharSequence cs) {
        return cs.toString().isBlank();
    }
    
    @PileDoc("Returns the true if the first string contains the second string.")
    @RenamedMethod("includes?")
    public static boolean doesInclude(CharSequence cs, CharSequence subseq) {
        return cs.toString().contains(subseq);
    }
    
    @Precedence(0)
    public static String join(Collection<?> col) {
        return join(",", col);
    }
    
    @Precedence(1)
    public static String join(CharSequence delim, Collection<?> col) {
        return col.stream()
                  .map(NativeCore::str)
                  .collect(Collectors.joining(delim));
    }

}
