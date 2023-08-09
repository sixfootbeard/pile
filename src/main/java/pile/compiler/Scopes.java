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

import static java.util.Objects.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import pile.compiler.form.VarScope;
import pile.core.Namespace;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileCompileException;
import pile.util.Pair;

/**
 * Encapsulates a ordered collection of {@link VarScope scopes} and their
 * corresponding bound symbols. Typically you will have many open scopes in
 * which symbols can be {@link #lookupSymbolScope(Symbol) resolved}.
 * 
 * <ol>
 * <li>(enterScope)
 * <li>(addCurrent)
 * <li>(leaveScope)
 * </ol>
 * 
 * @author john
 *
 */
public class Scopes {

    public static final int NO_INDEX = -2;
    private static final Symbol DOT_SYM = new Symbol("pile.core", ".");
	
	public record LocalRecord(String name, Class clazz, int slot, Object val){};
	
    public record ScopeLookupResult(Symbol fullSym, String sym, VarScope scope, String namespace, Integer index,
            Class<?> type, Object val) {
    }

	private final Deque<Pair<VarScope, List<LocalRecord>>> scope = new ArrayDeque<>();

	public void enterScope(VarScope vs) {
		scope.addLast(new Pair<>(vs, new ArrayList<>()));
	}
	
	public void leaveScope() {
		scope.removeLast();
	}
	
	public void addCurrent(String sym, Class<?> type) {
        addCurrent(sym, type, NO_INDEX, null);
    }
	
	public void addCurrent(String sym, Class<?> type, int slot, Object val) {
		LocalRecord lr = new LocalRecord(sym, type, slot, val);
		scope.getLast().right().add(lr);
	}
	
	public Deque<Pair<VarScope, List<LocalRecord>>> getScope() {
		return scope;
	}
	
    /**
     * Looks up what the supplied symbol refers to. Symbols can resolve to things
     * like locals, method arguments, closed over arguments, namespace defined
     * values, or can themselves be literals (this is not an exhaustive list).<br>
     * <br>
     * A symbol may be unresolved ({@link Symbol#getNamespace()} == null) or
     * resolved.<br>
     * <br>
     * If resolved then the namespace itself is resolved (eg. 'pile.io' in
     * pile.io/read). It must resolve to a namespace defined value, or literal. In
     * either case the actual value must be a {@link Namespace} or a {@link Class}.
     * <br>
     * <br>
     * If unresolved then each scope is inspected for a corresponding definition. If
     * none is found it is looked up in the {@link NativeDynamicBinding#NAMESPACE
     * current namespace}. If not found there then the symbol will attempted to be
     * interpreted as a {@link Namespace} literal (eg. pile.core) and if that fails
     * then as a class literal (eg. java.lang.Integer). <br>
     * <br>
     * If no match has been found at this point the method will return null to
     * indicate no matching symbol exists.
     * 
     * @param symbol The symbol to look up.
     * @return A record of information about the resolved symbol, or null if no
     *         matching symbol has been found.
     * @throws PileCompileException If there is no literal corresponding to a
     *                              resolved symbol's namespace (eg. a.b.c/method
     *                              when a.b.c is not a valid namespace).
     */
	public ScopeLookupResult lookupSymbolScope(Symbol symbol) {
		
	    String namespace = symbol.getNamespace();
		String name = symbol.getName();
		
		if (namespace != null) {
			// If it's already resolved just use that.
			ScopeLookupResult namespaceSLR = lookupNamespaceAndLiteral(new Symbol(namespace));
			ensureCompile(namespaceSLR != null, symbol, () -> "Could not determine symbol namespace '" + namespace
                    + "', did you forget to (require " + namespace + ")?");
			
			Object val = namespaceSLR.val();
			if (val instanceof Binding b) {
			    val = b.getValue();
			}
			if (val instanceof Namespace ns) {
			    Binding nsBind = Namespace.getIn(ns, name);
			    if (nsBind == null) {
			        // explicit, valid ns but bad sym within that:
			        // (denote pile.core.time :as t)
			        // (t/bad-method)
			        return null;
			    }
			    var nsName = nsBind.namespace();
			    var withType = symbol.withNamespace(nsName);
			    return new ScopeLookupResult(withType, name, VarScope.NAMESPACE, nsName, null, Binding.class, nsBind);
			}
			if (val instanceof Class cls) {
			    var withType = symbol.withNamespace(cls.getName());
			    return new ScopeLookupResult(withType, name, VarScope.JAVA_CLASS, cls.getName(), null, Class.class, cls);
			}
		}
		
		VarScope lastScope = null;
		Iterator<Pair<VarScope, List<LocalRecord>>> it = scope.descendingIterator();

		while (it.hasNext()) {
		    var pair = it.next();

		    VarScope seenScope = pair.left();
		    VarScope usedScope = nextScope(lastScope, seenScope);
		    List<LocalRecord> records = pair.right();
		    
            for (LocalRecord lr : records) {
                if (lr.name().equals(name)) {
                    return new ScopeLookupResult(null, name, usedScope, null, lr.slot(), lr.clazz(), lr.val());
                }
            }
            
            lastScope = usedScope;
		}
		
		return lookupNamespaceAndLiteral(symbol);
	}
	
	public static ScopeLookupResult lookupNamespaceAndLiteral(Symbol sym) {
	    return lookupNamespaceAndLiteral(NativeDynamicBinding.NAMESPACE.getValue(), sym);
	}
	
	public static ScopeLookupResult lookupNamespaceAndLiteral(Namespace ns, Symbol sym) {
        String name = sym.getName();
        Binding lookup = Namespace.getIn(ns, name);
        if (lookup != null) {
            sym = sym.withNamespace(lookup.namespace());
            var nsName = lookup.namespace();
            return new ScopeLookupResult(sym, name, VarScope.NAMESPACE, nsName, null, Binding.class, lookup);
        }

        if (name.contains(".")) {
            var pileNamespace = RuntimeRoot.get(name);
            if (pileNamespace != null) {
                return new ScopeLookupResult(sym, name, VarScope.LITERAL, null, null, Namespace.class, pileNamespace);
            }
            try {
                var javaClass = loadClass(name);
                return new ScopeLookupResult(sym, name, VarScope.LITERAL, null, null, Class.class, javaClass);
            } catch (ClassNotFoundException e) {
                // pass
            }
        }
        return null;
    }

    private VarScope nextScope(VarScope last, VarScope next) {
        if (last == null) {
            return next;
        }
        if ((last == VarScope.METHOD || last == VarScope.CLOSURE) && 
                (next == VarScope.METHOD || next == VarScope.METHOD_LET)) {
            return VarScope.CLOSURE;
        }
        return next;
    }
	
}
