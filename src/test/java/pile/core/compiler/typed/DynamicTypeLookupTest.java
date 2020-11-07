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
package pile.core.compiler.typed;

import static java.lang.invoke.MethodType.*;
import static org.junit.Assert.*;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;

import pile.compiler.typed.Any;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.TypeVarArg;

public class DynamicTypeLookupTest {

    @Test
    public void test() {
        testMatch(t(Any.class), t(String.class), t(String.class));
        testMatch(t(String.class), t(String.class), t(String.class));
        
    }
    
    @Test
    public void testCastedOneMatch() {
        // Even though the actual type is a string the casted type is a charsequence,
        // there's no charsequence type, so match.
        testMatch(t(CharSequence.class), t(String.class), t(String.class));
    }
    
    @Test
    public void testNarrow() {
        var match = testMatch(t(Any.class), t(String.class), 
                              t(String.class), t(CharSequence.class));
        assertEquals(t(String.class), match);
    }
    
    @Test
    public void testNarrowStickyStatic() {
        var match = testMatch(t(CharSequence.class), t(String.class), 
                              t(String.class), t(CharSequence.class));
        assertEquals(t(CharSequence.class), match);
    }

    private MethodType t(Class<?>... args) {
        return methodType(Object.class, args);
    }

    private MethodType testMatch(MethodType staticTypes, MethodType runtimeTypes, MethodType... candidate) {
        return testMatches(true, staticTypes, runtimeTypes, candidate).get();
    }

    private void testNoMatch(MethodType staticTypes, MethodType runtimeTypes, MethodType... candidate) {
        testMatches(false, staticTypes, runtimeTypes, candidate);
    }

    private Optional<MethodType> testMatches(boolean expectMatch, MethodType staticTypes, MethodType runtimeTypes,
            MethodType... candidate) {
        DynamicTypeLookup<MethodType> lookup = new DynamicTypeLookup<MethodType>(mt -> new TypeVarArg(mt, false));
        Optional<MethodType> maybeMatch = lookup.findMatchingTarget(staticTypes.parameterList(),
                runtimeTypes.parameterList(), Arrays.stream(candidate));

        if (expectMatch) {
            assertTrue(maybeMatch.isPresent());
        } else {
            assertTrue(maybeMatch.isEmpty());
        }
        return maybeMatch;
    }

}
