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

import static pile.nativebase.NativeCore.*;

import pile.core.binding.NativeDynamicBinding;

public class DefaultLogger implements Logger {

    private final Class<?> clazz;

    public DefaultLogger(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public void log(LogLevel level, String msg, Object... parts) {
        if (isEnabled(level)) {
            String filled = String.format(msg, parts);
            String withClass = String.format("[%s] %s: %s", clazz.getName(), level.name(), filled);
            prn(withClass);
        }
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        LogLevel rootLevel = NativeDynamicBinding.ROOT_LOG_LEVEL.deref();
        return level.ordinal() >= rootLevel.ordinal();
    }

}
