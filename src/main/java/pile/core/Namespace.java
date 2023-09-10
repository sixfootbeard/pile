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

import static java.util.Objects.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.SwitchPoint;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.compiler.Scopes;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.core.binding.ImmutableBinding;
import pile.core.binding.IntrinsicBinding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.binding.ReferenceBinding;
import pile.core.binding.Unbound;
import pile.core.indy.PileMethodLinker;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.util.Pair;

public class Namespace {


    private static final Logger LOG = LoggerSupplier.getLogger(Namespace.class);    

	private final PersistentMap FINAL_META = PersistentMap.createArr(PileMethodLinker.FINAL_KEY, true);
	
	private static final AtomicLong suffix = new AtomicLong();
	
	private record Wrapper(Binding binding, String ns, boolean ours) {}

	private final Map<String, Wrapper> ourBindings = new ConcurrentHashMap<>();
	
	private final String name;

	public Namespace(String name) {
		this(name, Collections.emptyList());
	}

	public Namespace(String name, List<Namespace> autoImports) {
		this.name = name;
		autoImports.forEach(ai -> referFrom(ai));
	}

	/**
	 * Define a binding in this namespace.
	 * 
	 * @param name The name of the binding
	 * @param newValue    The value. May be null if nominally defining a binding to assist
	 *             in circular definitions.
	 */
	public void define(String name, Binding newValue) {
		AtomicReference<SwitchPoint> sp = new AtomicReference<>();

		ourBindings.compute(name, (k, oldValue) -> {
		
		    if (oldValue == null) {
				if (newValue == null) {
					return new Wrapper(new Unbound(getName(), false), getName(), true);
				} else {
					return new Wrapper(newValue, getName(), true);
				}
			} else {
				if (PileMethodLinker.isFinal(oldValue.binding()) && 
						! Unbound.isUnbound(oldValue.binding())) {
				    throw new IllegalArgumentException("Cannot override final binding: " + this.name + "/" + name);
				}
				
				if (oldValue.ours()) {
					// supplanted
					SwitchPoint toInvalidate = oldValue.binding().getSwitchPoint();
					if (! Unbound.isUnbound(oldValue.binding())) {
					    if (newValue != null && (oldValue.binding().isMacro() ^ newValue.isMacro())) {
					        throw new IllegalArgumentException("Old and new bindings must both be macros or both not.");
					    }
					    requireNonNull(toInvalidate, "Invalidated binding must have a switchpoint.");
					}
                    sp.set(toInvalidate);
				}

				if (newValue == null) {
					// null unsets mapping in compute, return singleton
					return new Wrapper(new Unbound(getName(), false), getName(), true);
				} else {
					return new Wrapper(newValue, getName(), true);
				}
				
			}
		});

		SwitchPoint toInvalidate = sp.get();

		// TODO How to manage other vars in our namespace?
		if (toInvalidate != null) {
		    LOG.trace("Invalidating switchpoint for %s/%s", getName(), name);
			SwitchPoint.invalidateAll(new SwitchPoint[] { toInvalidate });
		}
	}
	
	public void defineIfAbsent(String name, Binding v) {
	    ourBindings.compute(name, (k, ov) -> {
            if (ov == null) {
                return new Wrapper(v, getName(), true);
            }
            return ov;
        });
    }

    /**
	 * Return any symbol created from this namespace. Ignores all imported symbols.
	 * 
	 * @param name Symbol name
	 * @return A binding of the symbol, or null.
	 */
	public Binding getLocal(String name) {
		var b = ourBindings.get(name);
		if (b != null && b.ours()) {
			return b.binding();
		}
		return null;
	}
	
	public Map<String, Binding> getOurs() {
	    return ourBindings.entrySet().stream()
	                .filter(e -> e.getValue().ours())
	                .map(e -> Map.entry(e.getKey(), e.getValue().binding()))
	                .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));
	}

	public void referFrom(Namespace other) {
	    requireNonNull(other, "Other namespace may not be null.");
		for (Entry<String, Wrapper> entry : other.ourBindings.entrySet()) {
		    String symName = entry.getKey();
		    // TODO Use below instead
			Wrapper wrapper = entry.getValue();
            if (wrapper.ours()) {
			    LOG.trace("Importing [%s <- %s]: %s (%s)", this.getName(), other.getName(), symName, wrapper.binding().getValue());
                Binding ref = new ReferenceBinding(this, other, symName, PersistentMap.empty());
			    Wrapper bt = new Wrapper(ref, wrapper.ns(), false);
                ourBindings.put(symName, bt);
			}
		}
	}
	
	public void referOne(Namespace other, String symName, String thisSymName) {
	    Wrapper wrapper = other.ourBindings.get(symName);
        if (wrapper.ours()) {
            LOG.trace("Importing [%s <- %s]: %s (%s)", this.getName(), other.getName(), symName, wrapper.binding().getValue());
            Binding ref = new ReferenceBinding(this, other, symName, PersistentMap.empty());
            Wrapper bt = new Wrapper(ref, wrapper.ns(), false);
            ourBindings.put(thisSymName, bt);
        }
	}
	
	public void importForm(Class<?> clazz) {
		createClassSymbol(clazz.getSimpleName(), clazz);
	}
	
	public void createClassSymbol(String shortName, Class<?> longName) {
		ImmutableBinding b = new ImmutableBinding(name, BindingType.VALUE, longName, FINAL_META, null);
		ourBindings.put(shortName, new Wrapper(b, getName(), true));
	}
	
	public String getName() {
		return name;
	}

	public long getSuffix() {
		return suffix.getAndIncrement();
	}

	

	@Override
	public String toString() {
		return getName();
	}

	/**
	 * Return any visible symbol binding as seen from the current namespace. These
	 * symbols may have been imported from other namespaces.
	 * 
	 * @param symbol
	 * @return
	 */
	public static Binding getInCurrentNs(String symbol) {
		Namespace ns = NativeDynamicBinding.NAMESPACE.getValue();
		return getIn(ns, symbol);
	}

	/**
	 * Return any visible symbol binding as seen from the provided ns. These symbols
	 * may have been imported from other namespaces.
	 * 
	 * @param ns
	 * @param symbol
	 * @return
	 */
	public static Binding getIn(Namespace ns, String symbol) {
	
		if (ns != null) {
			var ourBinding = ns.ourBindings.get(symbol);
			if (ourBinding != null) {
				return ReferenceBinding.maybeDeref(ourBinding.binding());
			}
	
		}
		for (IntrinsicBinding c : IntrinsicBinding.values()) {
			if (symbol.equals(c.getName())) {
				return c;
			}
		}
	
		return null;
	}


	public static Optional<Class<?>> lookupClassNameSymbol(Namespace ns, String symbol) {
		var bind = ns.ourBindings.get(symbol);
		if (bind != null && 
		        bind.binding().getValue() instanceof Class c) {
			return Optional.of(c);
		}
		return Optional.empty();
	}

    public Var getVar(Symbol sym) {
        // foo
        // shortname/foo
        // long.name/foo
        
        final String name = sym.getName();
        
        final Namespace ns;
        if (sym.getNamespace() == null) {
            ns = this;
        } else {
            ScopeLookupResult slr = Scopes.lookupNamespaceAndLiteral(this, new Symbol(sym.getNamespace()));
            if (Namespace.class.equals(slr.type())) {
                ns = (Namespace) slr.val();
            } else if (Binding.class.equals(slr.type())) {
                Object val = slr.val();
                // TODO Find a way to fix this.
                if (val instanceof Binding b) {
                    val = b.getValue();
                }
                if (val instanceof Namespace boundNs) {
                    ns = boundNs;
                } else {
                    throw new IllegalArgumentException("Cannot create var for:" + sym
                            + ", the symbol namespace resolves to not a namespace:" + val.getClass());
                }
            } else {
                throw new IllegalArgumentException("Bad var namespace type");
            }            
        }
        
        Binding bind = Namespace.getIn(ns, name);
        var varNs = RuntimeRoot.get(bind.namespace());
        requireNonNull(bind, () -> "Could not find " + varNs + "/" + name);
        final Var v;
        if (PileMethodLinker.isFinal(bind)) {
            v = new FinalVar<>(varNs, name, bind);
        } else {
            v = new IndirectVar<>(varNs, name);
        }
        return v;
    }

}
