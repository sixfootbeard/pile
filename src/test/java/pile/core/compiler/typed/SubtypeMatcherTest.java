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

import org.junit.Test;

import pile.compiler.typed.SubtypeMatcher;
import pile.compiler.typed.TypeVarArg;

public class SubtypeMatcherTest {

    @Test
    public void testSub() {
        testMatch(t(String.class), t(Object.class));
    }

    @Test
    public void testSubPrimitive() {
        testMatch(t(int.class), t(Object.class));
        testNoMatch(t(Object.class), t(int.class));
    }

    @Test
    public void testExact() {
        testMatch(t(String.class), t(String.class));
    }
    
    @Test
    public void testIntf() {
        testMatch(t(String.class), t(CharSequence.class));
    }


    @Test
    public void testNoMatch() {
        testNoMatch(t(Object.class), t(String.class));
    }

    @Test
    public void testNoMatchNonObject() {
        testNoMatch(t(CharSequence.class), t(String.class));
    }

    private MethodType t(Class<?>... args) {
        return methodType(Object.class, args);
    }

    private void testMatch(MethodType staticTypes, MethodType... candidate) {
        testMatches(true, staticTypes, candidate);
    }

    private void testNoMatch(MethodType staticTypes, MethodType... candidate) {
        testMatches(false, staticTypes, candidate);
    }

    private void testMatches(boolean expectMatch, MethodType staticTypes, MethodType... candidate) {
        SubtypeMatcher<MethodType> lookup = new SubtypeMatcher<>(mt -> new TypeVarArg(mt, false));
        Optional<MethodType> maybeMatch = lookup.findFirstMatch(staticTypes.parameterList(), Arrays.stream(candidate));
        if (expectMatch) {
            assertTrue(maybeMatch.isPresent());
        } else {
            assertTrue(maybeMatch.isEmpty());
        }
    }
}
