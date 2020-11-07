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
package pile.compiler;

import java.util.regex.Pattern;

/**
 * Form that the compiler considers a 'regex' (along with an actual
 * {@link Pattern}) so that we don't need to AOT compile the string and can use
 * condy. Unfortunately if the regex literal is subsequently used in a macro it
 * will have to be realized as a {@link Pattern} at that point so that users
 * only have to interact with {@link Pattern} forms of regex literals.
 *
 */
public class DeferredRegex {

	private final String pattern;

	public DeferredRegex(String pattern) {
		super();
		this.pattern = pattern;
	}

	public String getPattern() {
		return pattern;
	}
}
