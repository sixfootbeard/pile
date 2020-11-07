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

import java.lang.reflect.Method;

public interface Logger {
	
	public void log(LogLevel level, String msg, Object... parts);
	
	public boolean isEnabled(LogLevel level);
	
	public default void error(String msg, Object... parts) {
		log(LogLevel.ERROR, msg, parts);
	}
	
	public default void warn(String msg, Object... parts) {
		log(LogLevel.WARN, msg, parts);
	}
	
	public default void info(String msg, Object... parts) {
		log(LogLevel.INFO, msg, parts);
	}
	
	public default void debug(String msg, Object... parts) {
		log(LogLevel.DEBUG, msg, parts);
	}
	
	public default void trace(String msg, Object... parts) {
		log(LogLevel.TRACE, msg, parts);
	}
	
	public default void errorEx(String string, Throwable t, Object... args) {
		logEx(LogLevel.ERROR, string, t, args);
	}
	public default void warnEx(String string, Throwable t, Object... args) {
		logEx(LogLevel.WARN, string, t, args);
	}
	public default void infoEx(String string, Throwable t, Object... args) {
		logEx(LogLevel.INFO, string, t, args);
	}
	public default void debugEx(String string, Throwable t, Object... args) {
		logEx(LogLevel.DEBUG, string, t, args);
	}
	public default void traceEx(String string, Throwable t, Object... args) {
		logEx(LogLevel.TRACE, string, t, args);
	}

	public default void logEx(LogLevel level, String fmt, Throwable t, Object... args) {
		if (isEnabled(level)) {
			log(level, fmt, args);
			// Maybe?
			log(level, "%s", t.toString());
		}
		
	}
}
