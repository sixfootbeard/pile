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
package pile.compiler.macro;

import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.nativebase.NativeCore.*;

import java.util.Optional;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MacroEvaluated;
import pile.compiler.form.Form;
import pile.compiler.form.SExpr;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.IntrinsicBinding;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.ParserConstants;
import pile.core.parse.TypeTag;
import pile.nativebase.NativeCore;
import pile.util.Pair;

public class UnquoteForm implements Form {
	
	private static final Logger LOG = LoggerSupplier.getLogger(UnquoteForm.class);
	
	private static final Symbol UNQUOTE_SYM = new Symbol("pile.core", "unquote");

	private final Object form;
	private final Namespace ns;

	public UnquoteForm(Object form) {
		this.form = second(form);
		this.ns = NAMESPACE.getValue();
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {		
		return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.UNQUOTE,
		        (CompilerState cs) -> {
			LOG.trace("Compiling: %s", form);
			Compiler.compile(cs, form);
		});
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
		LOG.trace("Evaluating: %s", form);
		return Compiler.evaluate(cs, form);
	}

}
