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
package pile.util;

import pile.core.Keyword;

public class CommonConstants {

    public static final Keyword DOC = Keyword.of("doc");

    public static final Keyword ARG_LIST = Keyword.of("arg-lists");

    public static final Keyword DYNAMIC = Keyword.of("dynamic");

    public static final Keyword SCOPED = Keyword.of("scoped");

    public static final Keyword AS = Keyword.of("as");

    public static final Keyword REFER = Keyword.of("refer");

    public static final Keyword RENAME = Keyword.of("rename");

    public static final Keyword NATIVE_SOURCE = Keyword.of(CommonConstants.PILE_CORE_NS, "native-source");

    public static final String PILE_CORE_NS = "pile.core";

}
