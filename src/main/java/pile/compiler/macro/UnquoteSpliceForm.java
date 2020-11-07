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
import static pile.nativebase.NativeCore.*;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MacroEvaluated;
import pile.compiler.form.Form;
import pile.core.Keyword;
import pile.core.binding.IntrinsicBinding;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.TypeTag;

public class UnquoteSpliceForm implements Form {
	private static final Logger LOG = LoggerSupplier.getLogger(UnquoteSpliceForm.class);

	private final Object form;

	public UnquoteSpliceForm(Object form) {
		this.form = second(form);
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		LOG.trace("Compiling: %s", form);
		UnquoteForm delegate = new UnquoteForm(PersistentList.reversed(QuoteForm.QUOTE_SYM, form));
		return delegate.compileForm(compilerState)
		               .withRef(IntrinsicBinding.UNQUOTE_SPLICE);
	}
	
//	@Override
//	public DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {
//	    LOG.trace("Macro Compiling: %s", form);
//        UnquoteForm delegate = new UnquoteForm(PersistentList.reversed(QuoteForm.QUOTE_SYM, form));
//        return delegate.macroCompileForm(compilerState, context)
//                       .withRef(IntrinsicBinding.UNQUOTE_SPLICE);
//	}
	

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
		throw notYetImplemented("Unsplice eval");
	}
	
	@Override
	public MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
		LOG.trace("Evaluating: %s", form);
		UnquoteForm delegate = new UnquoteForm(form);
		MacroEvaluated mev = delegate.macroEvaluateForm(cs, context);
		return new MacroEvaluated(mev.result(), true);
	}

}
