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
package pile.core.log;

import static pile.compiler.Helpers.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

public class LoggerSupplier {

	public static final String PILE_LOGGER_CLASS = "pile.logger.class";
	
	private static final Function<Class<?>, Logger> LOGGER_SUPPLIER;

	static {
		String loggerClassStr = System.getProperty(PILE_LOGGER_CLASS);
		if (loggerClassStr == null) {
			LOGGER_SUPPLIER = DefaultLogger::new;
		} else {
			try {
				Class<?> lClass = loadClass(loggerClassStr);
				Constructor<?> cons = lClass.getConstructor(Class.class);
				LOGGER_SUPPLIER = (clazz) -> {
					try {
						return (Logger) cons.newInstance(clazz);
					} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
							| InvocationTargetException e) {
						throw new IllegalArgumentException("Bad logger class: " + loggerClassStr, e);
					}
				};
			} catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
				throw new IllegalArgumentException("Bad logger class: " + loggerClassStr, e);
			}
		}

	}

	public static Logger getLogger(Class<?> clazz) {
		return LOGGER_SUPPLIER.apply(clazz);
	}

}
