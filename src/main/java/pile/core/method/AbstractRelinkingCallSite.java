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
package pile.core.method;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;
import java.util.List;

import pile.core.Namespace;
import pile.core.exception.PileInternalException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;

public abstract class AbstractRelinkingCallSite extends MutableCallSite {
	
	private static final Logger LOG = LoggerSupplier.getLogger(AbstractRelinkingCallSite.class);

	private static final MethodHandle RELINK;

	static {
		try {
			RELINK = lookup().findVirtual(AbstractRelinkingCallSite.class, "relink",
					methodType(Object.class, Object[].class));
		} catch (ReflectiveOperationException e) {
			throw new PileInternalException(e);
		}
	}

	protected final MethodHandle relink;

	public AbstractRelinkingCallSite(MethodType type)  {
		super(type); // argument type +1?
		this.relink = RELINK.bindTo(this).asCollector(Object[].class, this.type().parameterCount()).asType(this.type());
		setTarget(relink);
	}

	@SuppressWarnings("unused")
	private Object relink(Object[] args) throws Throwable {
		MethodHandle handle = findHandle(args).asType(type());		
		setTarget(handle);		
		return handle.invokeWithArguments(args);
	}
	
	protected abstract MethodHandle findHandle(Object[] args) throws Throwable;

}
