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
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MacroEvaluated;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.compiler.form.Form;
import pile.compiler.form.SExpr;
import pile.compiler.form.VarScope;
import pile.core.Keyword;
import pile.core.Metadata;
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

public class SyntaxQuoteForm implements Form {

    public static Keyword SYNTAX_QUOTE_KW = Keyword.of("syntax-quote");
	
	private static final Logger LOG = LoggerSupplier.getLogger(SyntaxQuoteForm.class);
	private static final Symbol SYNTAX_QUOTE_SYM = new Symbol("pile.core", "syntax-quote");

	private final Object form;
	private final Namespace ns;

	public SyntaxQuoteForm(Object form) {

		this.ns = NAMESPACE.getValue();
        var propMeta = meta(form);
        
        var raw = second(form);
        // ^:abcd `a 
        // ^:abcd (syntax-quote a)
        // We have to propagate meta
        if (raw instanceof Metadata mo) {
            this.form = mo.updateMeta(map -> merge(map, propMeta));
        } else {
            this.form = raw;
        } 
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.SYNTAX_QUOTE, (CompilerState cs) -> {
            LOG.trace("Compiling: %s", form);
            try {
                cs.pushAutoGensymScope();
                Compiler.macroCompile(cs, form, SYNTAX_QUOTE_KW);
            } finally {
                cs.popAutoGensymScope();
            }
		});
	}

	@Override
    public MacroEvaluated evaluateForm(CompilerState cs) throws Throwable {
        LOG.trace("Evaluating: %s", form);
        try {
            cs.pushAutoGensymScope();
            return Compiler.macroEval(cs, form, SYNTAX_QUOTE_KW);
        } finally {
            cs.popAutoGensymScope();
        }
	}

//	@Override
//	public MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
//		return evaluateForm(cs);
//	}

}
