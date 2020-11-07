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
package pile.core;

import static org.junit.Assert.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.MacroEvaluated;
import pile.core.parse.PileParser;

public class TestHelpers {

	public static PersistentList parse(String string) {
		PileParser p = new PileParser();
		try (ByteArrayInputStream bais = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8))) {
			return p.parse(bais, "<stdin>");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void pre(String s) {
		try {
			Compiler.evaluate(parse(s));
		} catch (RuntimeException re) {
			throw re;
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		}
	}

	public static Object eval(String string) {
		try {
			return Compiler.evaluate(new CompilerState(), first(parse(string)));
		} catch (RuntimeException re) {
			throw re;
		} catch (Throwable e) {	
			e.printStackTrace();
			fail();
			return null;
		}
	}
	
	public static Object e(String string) {
        try {
            return Compiler.evaluate(new CompilerState(), first(parse(string)));
        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable e) { 
            e.printStackTrace();
            fail();
            return null;
        }
    }
}
