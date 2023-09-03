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
package pile.compiler.form;

import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.core.indy.IndyHelpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Optional;
import java.util.regex.Pattern;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.DeferredRegex;
import pile.core.Namespace;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.util.ConstantDynamicBootstrap;

public class RegexForm implements Form {

	protected String patternStr;
	protected Pattern pattern;
	protected final Namespace ns;

	public RegexForm(Object form) {
		this(form, NAMESPACE.getValue());
	}

	public RegexForm(Object form, Namespace ns) {
		if (form instanceof Pattern p) {
			this.pattern = p;
		} else if (form instanceof DeferredRegex dr) {
			this.patternStr = dr.getPattern();
		} else {
			throw new PileCompileException("Unexpeced regex form type: " + form.getClass(), LexicalEnvironment.extract(form));
		}
		this.ns = ns;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		String condyDescriptor = getConstantBootstrapDescriptor(Pattern.class, STRING_TYPE);
		var condy = makeCondy("regex", RegexForm.class, "bootstrap", condyDescriptor, Pattern.class,
				getPatternString());
		return new DeferredCompilation(TypeTag.REGEX, getRawForm(), Optional.of(condy), (cs) -> {
			cs.getCurrentMethodVisitor().visitLdcInsn(condy);
			cs.getMethodStack().push(Pattern.class);
		});
	}
	
	private Object getRawForm() {
	    return pattern == null ? patternStr : pattern;
	}

	@Override
	public Pattern evaluateForm(CompilerState cs) throws Throwable {
		return getPattern();
	}

	private String getPatternString() {
		if (patternStr == null) {
			patternStr = pattern.pattern();
		}
		return patternStr;
	}
	
	private Pattern getPattern() {
		if (pattern == null) {
			pattern = Pattern.compile(patternStr);
		}
		return pattern;
	}

	@ConstantDynamicBootstrap
	public static Pattern bootstrap(Lookup lookup, String name, Class<Pattern> clazz, String pattern) {
		return Pattern.compile(pattern);
	}

}
