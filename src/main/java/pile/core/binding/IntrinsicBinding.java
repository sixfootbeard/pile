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
package pile.core.binding;

import java.lang.invoke.SwitchPoint;
import java.util.function.Function;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.compiler.form.AnonClassForm;
import pile.compiler.form.AnonFnForm;
import pile.compiler.form.CaseForm;
import pile.compiler.form.CastForm;
import pile.compiler.form.DefForm;
import pile.compiler.form.DefGenericForm;
import pile.compiler.form.DefImplForm;
import pile.compiler.form.DefProtocolForm;
import pile.compiler.form.DefTypeForm;
import pile.compiler.form.DoForm;
import pile.compiler.form.ExtendsForm;
import pile.compiler.form.Form;
import pile.compiler.form.IfForm;
import pile.compiler.form.ImportForm;
import pile.compiler.form.InteropForm;
import pile.compiler.form.LetForm;
import pile.compiler.form.LockForm;
import pile.compiler.form.LoopForm;
import pile.compiler.form.MethodForm;
import pile.compiler.form.MonitorEnterForm;
import pile.compiler.form.MonitorExitForm;
import pile.compiler.form.NewForm;
import pile.compiler.form.NamespaceForm;
import pile.compiler.form.RecurForm;
import pile.compiler.form.SetForm;
import pile.compiler.form.VarForm;
import pile.compiler.form.exception.ThrowForm;
import pile.compiler.form.exception.TryForm;
import pile.compiler.macro.QuoteForm;
import pile.compiler.macro.SyntaxQuoteForm;
import pile.compiler.macro.UnquoteForm;
import pile.compiler.macro.UnquoteSpliceForm;
import pile.core.Metadata;

public enum IntrinsicBinding implements Binding {
    DEF("def", DefForm::new),
    FN("fn*", MethodForm::new),
    LET("let*", LetForm::new),
    DO("do", DoForm::new),
    IF("if", IfForm::new),
    CASE_FORM("case", CaseForm::new),
    CAST("cast", CastForm::new),
    
    // Loops
    LOOP("loop*", LoopForm::new),
    RECUR("recur", RecurForm::new),
    
    // locking
    LOCK("locking", LockForm::new),
    MONITOR_ENTER("monitor-enter", MonitorEnterForm::new),
    MONITOR_EXIT("monitor-exit", MonitorExitForm::new),
    
    VAR("var", VarForm::new),
    SET("set!", SetForm::new),
    
    // Type creation
    DEF_TYPE("deftype", DefTypeForm::new),
    ANON_FN("anon-fn", AnonFnForm::new),
    ANON_CLASS("anon-cls", AnonClassForm::new),
    
    // Java interop
    INTEROP(".", InteropForm::new),
    NEW("new", NewForm::new),
    
    // Namespace
    NS("ns", NamespaceForm::new), 
    
    // Macros
    // DEFMACRO is in core.pile bootstrap
    QUOTE("quote", true, QuoteForm::new),
    SYNTAX_QUOTE("syntax-quote", true, SyntaxQuoteForm::new),
    UNQUOTE("unquote", true, UnquoteForm::new),
    UNQUOTE_SPLICE("unquote-splice", true, UnquoteSpliceForm::new),
    
    // Exceptions
    TRY_FORM("try", TryForm::new),
    THROW_FORM("throw", ThrowForm::new),
    
    // Protocols
    DEFPROTOCOL("defprotocol", DefProtocolForm::new),
    EXTENDS("extend*", ExtendsForm::new),
    
    DEFGENERIC("defgeneric", DefGenericForm::new),
    DEFIMPL("defimpl", DefImplForm::new),
    
    ;
	

    private final String name;

	private final Function<PersistentList, Form> cons;
	
	private final boolean isMacro;

    IntrinsicBinding(String name, Function<PersistentList, Form> cons) {
    	this(name, false, cons);
    }
    
    IntrinsicBinding(String name, boolean isMacro, Function<PersistentList, Form> cons) {
        this.name = name;
        this.isMacro = isMacro;
        this.cons = cons;
    }
    
    @Override
    public boolean isMacro() {
		return isMacro;
	}
    
    public Function<PersistentList, Form> getCons() {
		return cons;
	}

    @Override
    public PersistentMap meta() {
        return PersistentMap.createArr(Binding.BINDING_TYPE_KEY, BindingType.INTRINSIC);
    }

    @Override
    public Metadata withMeta(PersistentMap newMeta) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getValue() {
        return this;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return null;
    }

//    @Override
//    public BindingType getType() {
//        return BindingType.INTRINSIC;
//    }

    @Override
    public String namespace() {
        return "pile.core";
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
    	return String.format("%s(intrinsic)", getName());
    }

}
