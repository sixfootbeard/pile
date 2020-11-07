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
            
    public static void new_builder() {
        StringBuilder sb = new StringBuilder();
        PersistentList<StringBuilder> newList = (PersistentList<StringBuilder>) conj(BUILDER.getValue(), sb);
        BUILDER.set(newList);
    }       
            
    public static void append(Object o) {
        StringBuilder head = BUILDER.getValue().head();
        append(head, o);
    }
    
    public static void append(StringBuilder sb, Object o) {
        sb.append(NativeCore.str(o));
    }
    
    public static String build(StringBuilder sb) {
        return sb.toString();
    }
    
    public static String build() {
        PersistentList<StringBuilder> pl = BUILDER.getValue();
        StringBuilder head = pl.head();
        BUILDER.set(pl.pop());
        return build(head);
    }

    public static int length(CharSequence cs) {
        String s = cs.toString();
        return s.codePointCount(0, s.length());
    }
    
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
    
    @RenamedMethod("blank?")
    public static boolean isBlank(CharSequence cs) {
        return cs.toString().isBlank();
    }
    
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
