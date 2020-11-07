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
package pile.core.indy.guard;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pile.core.exception.PileInternalException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;

/**
 * Create a {@link MethodHandle} appropriate to guard the receiver type.
 * 
 */
public class ReceiverTypeGuard {

	private static final Logger LOG = LoggerSupplier.getLogger(ReceiverTypeGuard.class);

	private final Class<?> clazz;
	private final MethodHandle relink;
	private final MethodHandle target;

	public ReceiverTypeGuard(Class<?> clazz, MethodHandle target, MethodHandle relink) {
		this.clazz = clazz;
		this.target = target;
		this.relink = relink;
	}

	public MethodHandle createTypeGuard() {
		LOG.trace("Creating receiver type guard for: %s", clazz);

		MethodHandle eq, getClass;
		try {
			eq = lookup().findVirtual(Class.class, "equals", methodType(boolean.class, Object.class));
			getClass = lookup().findVirtual(Object.class, "getClass", methodType(Class.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new PileInternalException("Cannot find equals", e);
		}

		MethodHandle boundEq = eq.bindTo(clazz);
		// TODO Move the type generics down to the guard out of this test
		MethodHandle test = filterArguments(boundEq, 0, getClass.asType(getClass.type().generic()));
		test = test.asType(test.type().changeParameterType(0, target.type().parameterType(0)));
		MethodHandle guardWithTest = guardWithTest(test, target, relink);

		return guardWithTest;
	}
	
	public MethodHandle createSubTypeTypeGuard() {
		LOG.trace("Creating receiver (sub)type guard for: %s", clazz);

		MethodHandle iaf, getClass;
		try {
			iaf = lookup().findVirtual(Class.class, "isAssignableFrom", methodType(boolean.class, Class.class));
			getClass = lookup().findVirtual(Object.class, "getClass", methodType(Class.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new PileInternalException("Cannot find equals", e);
		}

		MethodHandle boundEq = iaf.bindTo(clazz);
		MethodHandle test = filterArguments(boundEq, 0, getClass);
		test = test.asType(test.type().changeParameterType(0, target.type().parameterType(0)));
		MethodHandle guardWithTest = guardWithTest(test, target, relink);

		return guardWithTest;
	}

}
