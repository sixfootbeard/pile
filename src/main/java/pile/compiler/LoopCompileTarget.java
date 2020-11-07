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
package pile.compiler;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.objectweb.asm.Label;

import pile.compiler.ParameterParser.MethodParameter;

@SuppressWarnings("exports")
public record LoopCompileTarget(Label loopStart, int size, LoopTargetType ltt, List<ClassSlot> params,
        List<List<Class<?>>> recurTypes) {

}
