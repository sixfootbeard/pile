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
package pile.compiler.form;

import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.collection.PersistentArrayVector;
import pile.core.Namespace;
import pile.core.Symbol;

public class AnonFnForm implements Form {

	private final PersistentList form;
	private final Namespace ns;

	public AnonFnForm(PersistentList form) {
		this.ns = NAMESPACE.getValue();
		this.form = form;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
		PersistentList wrap = generateExpandedForm(form);
		return new MethodForm(wrap).compileForm(compilerState);
	}

	@Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
    	PersistentList wrap = generateExpandedForm(form);    
    	return new MethodForm(wrap).evaluateForm(cs);
    }

    public static PersistentList generateExpandedForm(PersistentList form) {
		// #(foo % a)
		// ~parsed: 
		// (anon-fn (foo % a))
		// ~expanded:
		// (fn* [P__1] (foo P__1 a))
		Map<Integer, Symbol> genSyms = new HashMap<>();
		PersistentList newForm = (PersistentList) expand(second(form), genSyms);
		
		PersistentList wrap = new PersistentList<>();
		
		wrap = wrap.conj(newForm);
		// push vec
		wrap = wrap.conj(makeArgs(genSyms));
		// push fn*
		wrap = wrap.conj(new Symbol("fn*"));
		return wrap;
	}

    private static PersistentArrayVector makeArgs(Map<Integer, Symbol> genSyms) {
        PersistentArrayVector pv = new PersistentArrayVector<>();
        if (!genSyms.isEmpty()) {
            Integer max = Collections.max(genSyms.keySet());

            for (int i = 0; i <= max; ++i) {
                pv = pv.conj(null);
            }
            for (var entry : genSyms.entrySet()) {
                pv = pv.assoc(entry.getKey(), entry.getValue());
            }
            for (int i = 0; i < max; ++i) {
                if (pv.get(i) == null) {
                    pv = pv.assoc(i, gensym());
                }
            }
        }
        return pv;
    }
	
	private static Object expand(Object o, Map<Integer, Symbol> genSyms) {
		if (o instanceof Symbol sym) {
			var s = strSym(sym);
			if (s.startsWith("%")) {
				if (s.equals("%")) {
					return genSyms.computeIfAbsent(0, k -> gensym());
				} else {
					var index = Integer.valueOf(s.substring(1));
					return genSyms.computeIfAbsent(index, k -> gensym());
				}
			} else {
				return sym;
			}
		} else if (o instanceof PersistentList pl) {
			List<Object> parts = new ArrayList<>();
			for (var p : pl) {
				parts.add(expand(p, genSyms));
			}
			return PersistentList.fromList(parts);
		} else if (o instanceof PersistentVector pv) {
			List<Object> parts = new ArrayList<>();
			for (var p : pv) {
				parts.add(expand(p, genSyms));
			}
			return PersistentVector.fromList(parts);
		} else if (o instanceof PersistentSet ps) {
			List<Object> parts = new ArrayList<>();
			for (var p : ps) {
				parts.add(expand(p, genSyms));
			}
			return PersistentSet.fromList(parts);
		} else if (o instanceof PersistentMap<?, ?> pm) {
			List<Object> parts = new ArrayList<>();
			var entrySet = pm.entrySet();
			for (var entry : entrySet) {
				parts.add(expand(entry.getKey(), genSyms));
				parts.add(expand(entry.getValue(), genSyms));
			}
			return PersistentMap.createArr(parts.toArray());
		} else {
			return o;
		}
	}

}
