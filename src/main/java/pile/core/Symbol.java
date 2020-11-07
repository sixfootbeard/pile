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

import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.ConstantDynamic;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentMap;
import pile.compiler.Constants;
import pile.compiler.Helpers;
import pile.compiler.form.SymbolForm;
import pile.compiler.sugar.StaticFieldDesugarer;
import pile.compiler.sugar.SymbolDesugarer;
import pile.compiler.typed.Any;
import pile.core.binding.Binding;
import pile.core.binding.NativeDynamicBinding;
import pile.core.exception.PileException;
import pile.core.hierarchy.PersistentMetadataObject;
import pile.core.parse.ParserConstants;

/**
 * A symbol is composed of two parts: a namespace (optional), and a name:
 * '(namespace/)?name'. <br>
 * <br>
 * Eg.
 * <ol>
 * <li>java.class.name/name (?)
 * <li>pile.namespace/name
 * <li>name
 * <li>java.class.name
 * <li>pile.namespace
 * </ol>
 * The namespace can either refer to another symbol which must resolve to a
 * Class instance, a pile namespace or a full.class.name.
 *
 */
public class Symbol extends PersistentMetadataObject<Symbol> implements Metadata, Named, ConstForm<ConstantDynamic> {

    private final String namespace;
    private final String name;

    public Symbol(String name) {
        this(null, name, PersistentHashMap.EMPTY);
    }

    public Symbol(String name, PersistentMap meta) {
        this(null, name, meta);
    }

    public Symbol(String namespace, String name) {
        this(namespace, name, PersistentHashMap.EMPTY);
    }

    public Symbol(String namespace, String name, PersistentMap meta) {
        super(meta);
        this.namespace = namespace;
        this.name = Objects.requireNonNull(name, "Symbol name may not be null");
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    public Class<?> getAnnotatedType(Namespace ns) {
        return Helpers.getTypeHint(this, ns).orElse(Any.class);
    }
    
    public Optional<Class<?>> tryGetAsClass(Namespace ns) {
        Optional<Class<?>> maybe;
        if (name.contains(".")) {
            try {
                maybe = Optional.of(loadClass(name));
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            }
        } else {
            if (namespace != null) {
                Namespace maybeNs = RuntimeRoot.get(namespace);
                if (maybeNs != null) {
                    ns = maybeNs;
                }
            }
            maybe = Namespace.lookupClassNameSymbol(ns, name);
        }
        return maybe;
    }

    /**
     * Return the symbol as a {@link Class}. The symbol may be a class literal (eg.
     * java.lang.Integer) or may be a defined value which resolves to a class (eg.
     * 'integer' in a namespace where (def integer java.lang.Integer) is defined).
     * 
     * @param ns
     * @return The class
     * @throws PileException If the symbol could not be resolved to a class.
     */
    public Class<?> getAsClass(Namespace ns) {
        return tryGetAsClass(ns)
                .orElseThrow(() -> new PileException("Expected symbol '" + this + "' to resolve to a class"));
    }

    /**
     * Create a new symbol by resolving the named symbol in the provided namespace.
     * If no symbol exists then the current namespace will be used. If this symbol
     * is already resolved then this is a no-op.
     * 
     * @param ns
     * @return
     */
    public Symbol maybeResolve(Namespace ns) {
        if (namespace != null) {
            return this;
        }
        Binding maybeNs = Namespace.getIn(ns, name);
        final String nsStr;
        if (maybeNs != null) {
            nsStr = maybeNs.namespace();
        } else {
            nsStr = NativeDynamicBinding.NAMESPACE.deref().getName();
        }
        return this.withNamespace(nsStr);
    }

    @Override
    protected String computeStr() {
        StringBuilder sb = new StringBuilder();
        if (namespace != null) {
            sb.append(namespace);
            sb.append("/");
        }
        sb.append(name);
        return sb.toString();
    }
    
    @Override
    protected int computeHash() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Symbol other = (Symbol) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (namespace == null) {
            if (other.namespace != null)
                return false;
        } else if (!namespace.equals(other.namespace))
            return false;
        return true;
    }

    @Override
    public Optional<ConstantDynamic> toConst() {
        String desc = getConstantBootstrapDescriptor(Symbol.class, STRING_TYPE, STRING_TYPE,
                getType(PersistentMap.class));
        Optional<Object> maybeMetaForm = this.meta().toConst();
        Object namespaceForm = Constants.toConstAndNull(namespace).get();

        return maybeMetaForm.map(metaForm -> makeCondy("make", SymbolForm.class, "mbootstrap", desc, Symbol.class,
                namespaceForm, this.getName(), metaForm));
    }

    public Symbol withName(String newName) {
        return new Symbol(namespace, newName, meta());
    }

    public Symbol withNamespace(String newNs) {
        return new Symbol(newNs, name, meta());
    }

    public Symbol withTypeAnnotation(Class<?> clazz) {
        PersistentMap m = meta();
        Symbol csym = new Symbol(clazz.getName());
        m = m.assoc(ParserConstants.ANNO_TYPE_KEY, csym);
        return new Symbol(namespace, name, m);
    }

    @Override
    protected Symbol copyWithMeta(PersistentMap newMeta) {
        return new Symbol(namespace, name, newMeta);
    }

}
