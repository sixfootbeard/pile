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

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;

import pile.core.binding.NativeDynamicBinding;

public class AbstractTest {
	
	protected static AtomicLong suf = new AtomicLong();

	protected String nsStr;

	@Before
	public final void abstractSetup() {
		// Each test gets its own namespace to operate in
		nsStr = "pile.test.interop." + suf.getAndIncrement();
		Namespace ns = RuntimeRoot.defineOrGet(nsStr);
		NativeDynamicBinding.NAMESPACE.set(ns);
	}

}
