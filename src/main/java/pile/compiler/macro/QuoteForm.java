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

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MacroEvaluated;
import pile.compiler.form.Form;
import pile.compiler.form.SExpr;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.TypeTag;
import pile.nativebase.NativeCore;

public class QuoteForm implements Form {

    public static Keyword QUOTE_KW = Keyword.of("quote");

	private static final Logger LOG = LoggerSupplier.getLogger(QuoteForm.class);
	static final Symbol QUOTE_SYM = new Symbol("pile.core", "quote");

	private final Object form;
	protected final Namespace ns;

//    private final PersistentMap propMeta;

	public QuoteForm(Object form) {
		
		this.ns = NAMESPACE.getValue();
		var propMeta = meta(form);
		
		var raw = second(form);
		// ^:abcd 'a 
		// ^:abcd (quote a)
        // We have to propagate meta
        if (raw instanceof Metadata mo) {
            this.form = mo.updateMeta(map -> merge(map, propMeta));
        } else {
            this.form = raw;
        } 
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		LOG.trace("Compiling: %s", form);
		return Compiler.macroCompileDefer(compilerState, form, QUOTE_KW);
	}

	@Override
	public Object evaluateForm(CompilerState cs) throws Throwable {
	    return MacroEvaluated.unwrap(macroEvaluateForm(cs, QUOTE_KW));
	}

	@Override
	public MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
	    LOG.trace("Macro Evaluating: %s", form);
	    return Compiler.macroEval(cs, form, QUOTE_KW);
	}

}
