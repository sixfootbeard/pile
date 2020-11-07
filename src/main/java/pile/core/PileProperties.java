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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentMap;
import pile.core.parse.PileParser;

public class PileProperties {

	public static PersistentMap PROPERTIES = PersistentHashMap.EMPTY;

	static {
		// read classpath "properties.pd"
		InputStream is = PileMain.class.getResourceAsStream("/properties.pd");
		if (is != null) {
			try {
				InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
				Object data = PileParser.parseData(isr);
				if (data instanceof PersistentMap hm) {
					PROPERTIES = hm;				
				}
			} catch (Throwable t) {
				t.printStackTrace();
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					// TODO log?
				}
			}
		}
	}
	
	public static boolean getBool(Object key, boolean ifNone) {
		return (boolean) PROPERTIES.get(key, ifNone);
	}
	
	public static Keyword getKeyword(Object key, Keyword ifNone) {
		return (Keyword) PROPERTIES.get(key, ifNone);
	}


}
