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
package pile.core.compiler;

import static org.junit.Assert.*;
import static pile.core.TestHelpers.*;

import org.junit.Ignore;
import org.junit.Test;

import pile.core.AbstractTest;

public class DefMacroTest extends AbstractTest {

	@Ignore
	@Test
	public void testSingle() {
		pre("""
				(defmacro infix [first-operand operator second-operand]
					(list operator first-operand second-operand))
				(def ff (fn* [a b c]
					(infix 2 + 1)))
				""");

		assertEquals(3L, eval("(eval (ff 1 + 2))"));
	}

}
