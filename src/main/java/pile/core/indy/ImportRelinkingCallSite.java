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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Objects;

import pile.compiler.Helpers;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.Unbound;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.LinkableMethod;
import pile.core.method.UnsatisfiableMethodCall;
import pile.nativebase.method.PileInvocationException;

/**
 * A call site which references an imported method, either native or compiled.
 * 
 * @implNote Threading Concerns: None. This callsite is only updated via {@link SwitchPoint} invalidation.
 *
 */
public class ImportRelinkingCallSite extends AbstractRelinkingCallSite {

	private static final Logger LOG = LoggerSupplier.getLogger(ImportRelinkingCallSite.class);

	private final String ns;
	private final String method;
	private final CompilerFlags flags;
    private final long anyMask;
    private final boolean allConstant;

    public ImportRelinkingCallSite(MethodType type, long anyMask, String ns, String method, boolean allConstant,
            CompilerFlags flags) {
        super(type);
        this.ns = ns;
        this.method = method;
        this.flags = flags;
        this.anyMask = anyMask;
        this.allConstant = allConstant;
    }
	
//	public ImportRelinkingCallSite(MethodHandle handle, long anyMask, SwitchPoint sp, String ns,
//			String method,  boolean allConstant, CompilerFlags flags) {
//		this(handle.type(), anyMask, ns, method, allConstant, flags);
//		setTarget(sp.guardWithTest(handle, relink));
//	}	

	@Override
	protected MethodHandle findHandle(Object[] args) throws Throwable {
		Binding binding = Namespace.getIn(RuntimeRoot.get(ns), method);
		if (Unbound.isUnbound(binding)) {
		    // stays unlinked.
            throw new PileInvocationException("Unbound function call: " + ns + "/" + method);
		}
		MethodType actualType = Helpers.createClassArray(args);
		MethodHandle newHandle;
        if (allConstant && Binding.getType(binding) == BindingType.VALUE
                && binding.getValue() instanceof LinkableMethod lm && lm.isPure()) {
            Object resultValue = lm.invoke(args);
            newHandle = constant(resultValue.getClass(), resultValue);
            newHandle = dropArgumentsToMatch(newHandle, 0, actualType.parameterList(), 0);
        } else {
            newHandle = getHandle(binding, actualType);
        }

				
		// Generalize type
		newHandle = newHandle.asType(type());

		// Compose switch point
		if (! PileMethodLinker.isFinal(binding)) {
			final SwitchPoint sp = binding.getSwitchPoint();
			Objects.requireNonNull(sp, "SwitchPoint should be non-null for non-final binding: " + getFullMethod());
			newHandle = sp.guardWithTest(newHandle, relink);
		}

		return newHandle;
	}

    private MethodHandle getHandle(Binding binding, MethodType actualType)
            throws Exception, NoSuchMethodException, IllegalAccessException {
        BindingType bindingType = Binding.getType(binding);
        MethodHandle newHandle = switch (bindingType) {
            case DYNAMIC, SCOPED -> PileMethodLinker.generateCallableDynamicHandle(ns, method, type());
            case VALUE -> {
                final Object val = binding.getValue();
                if (val instanceof LinkableMethod deref) {
                    yield deref.dynamicLink(CallSiteType.PLAIN, type(), anyMask, flags).dynamicInvoker();
                } else {
                    yield Helpers.getExceptionHandle(type(), UnsatisfiableMethodCall.class,
                            UnsatisfiableMethodCall::new, "Uncallable binding value: " + ns + "/" + method);
                }
            }
            default -> Helpers.getExceptionHandle(type(), UnsatisfiableMethodCall.class, 
                    UnsatisfiableMethodCall::new,
                    "Uncallable binding type [" + bindingType + "]: " + getFullMethod());
        };
        return newHandle;
    }

    private String getFullMethod() {
        return ns + "/" + method;
    }

}
