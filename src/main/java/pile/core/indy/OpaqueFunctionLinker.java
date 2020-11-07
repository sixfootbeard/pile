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
package pile.core.indy;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import pile.collection.PersistentMap;
import pile.compiler.Helpers;
import pile.core.PCall;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileException;
import pile.core.exception.PileInternalException;
import pile.core.indy.guard.ReceiverTypeGuard;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.LinkableMethod;
import pile.core.parse.LexicalEnvironment;
import pile.util.InvokeDynamicBootstrap;

/**
 * Links parameters that are used as function calls. These calls are potentially
 * going to involve more dynamic call sites than imported functions so they are
 * handled differently.
 * 
 * <pre>
 * (defn foo [some-fn] (some-fn "a" 1 true))
 * </pre>
 * 
 * Currently only works for PCall and defers to {@link PCall#invoke(Object...)}
 * which is suboptimal.
 *
 */
public class OpaqueFunctionLinker {

	private static final Logger LOG = LoggerSupplier.getLogger(OpaqueFunctionLinker.class);

	public static final MethodHandle CALL_PCALL, CALL_APPLY;

	static {
		try {
			CALL_PCALL = MethodHandles.lookup().findVirtual(PCall.class, "invoke",
					MethodType.methodType(Object.class, Object[].class));
			CALL_APPLY = MethodHandles.lookup().findVirtual(PCall.class, "applyInvoke",
	                    MethodType.methodType(Object.class, Object[].class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new PileInternalException(e);
		}
	}

	/**
	 * 
	 * @param caller
	 * @param name
	 * @param type
	 * @param namespaceStr The namespace of the fn name
	 * @param flags        {@link LinkOptions}
	 * @return
	 */
	@InvokeDynamicBootstrap
	public static CallSite bootstrap(Lookup caller, String name, MethodType type, PersistentMap lexMap,
			CompilerFlags flags, int constantFnVal) throws Throwable {
		// static: [receiver, arg0, arg1, ... argN]
		
		boolean constantFn = constantFnVal == 1;
		
	    MethodHandle collected;
		if (constantFn) {
            var cs = new AbstractRelinkingCallSite(type) {
                @Override
                protected MethodHandle findHandle(Object[] args) throws Throwable {
                    var withoutReceiver = type.dropParameterTypes(0, 1);
                    if (args[0] instanceof LinkableMethod lm) {
                        CallSite linked = lm.staticLink(CallSiteType.PLAIN, withoutReceiver, 0)
                                .orElseGet(() -> lm.dynamicLink(CallSiteType.PLAIN, withoutReceiver, 0, flags));
                        MethodHandle dropped = dropArguments(linked.dynamicInvoker(), 0, type.parameterType(0));
                        return dropped;
                    } else {
                        var msg = (args[0] == null) ? 
                                "Cannot call nil" : 
                                "Cannot call constant of type:" + args[0].getClass();
                        return getExceptionHandle(type, PileCompileException.class, PileCompileException::new, msg);
                    }
                }
            };
            collected = cs.dynamicInvoker();
		} else {
		    collected = CALL_PCALL.asCollector(1, Object[].class, type.parameterCount() - 1).asType(type);		
		}
		
		
		if (DebugHelpers.isDebugEnabled()) {
		    if (! lexMap.isEmpty()) {
		        Optional<LexicalEnvironment> maybeLex = LexicalEnvironment.fromMap(lexMap);
		        if (maybeLex.isPresent()) {
		            LexicalEnvironment lex = maybeLex.get();
	                String msg = String.format("Error calling opaque function [file=%s, line=%d, column=%d]",
	                        lex.getSource(), lex.getLineAt(), lex.getCharAtInLine());
	                collected = DebugHelpers.catchWithMessage(collected, msg);
		        }		    
		    }
        }
		return new ConstantCallSite(collected);
	}
}
