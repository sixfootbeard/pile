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

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.Constants;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MacroEvaluated;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.form.CollectionLiteralForm.LiteralDescriptor;
import pile.collection.PersistentArrayVector;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.binding.IntrinsicBinding;
import pile.core.indy.PersistentLiteralLinker;
import pile.core.parse.TypeTag;

/**
 * Compiles/Evaluates collection literal forms:
 * 
 * <ul>
 * <li>Vector:
 * 
 * <pre>
 * [1, "b"]
 * </pre>
 * 
 * <li>Map:
 * 
 * <pre>
 * {:a 12}
 * </pre>
 * 
 * <li>Set:
 * 
 * <pre>
 * #{:field}
 * </pre>
 * </ul>
 *
 * @param <T>
 */
public class CollectionLiteralForm<T> implements Form {

    public static final Handle LITERAL_BOOTSTRAP_HANDLE = new Handle(H_INVOKESTATIC,
            Type.getType(PersistentLiteralLinker.class).getInternalName(), "bootstrap",
            getBootstrapDescriptor(STRING_TYPE, OBJECT_ARRAY_TYPE), false);

    public static final Handle CONDY_BOOTSTRAP_HANDLE = new Handle(H_INVOKESTATIC,
            Type.getType(PersistentLiteralLinker.class).getInternalName(), "bootstrap",
            getConstantBootstrapDescriptor(Object.class, OBJECT_ARRAY_TYPE), false);

    public record LiteralDescriptor<T> (Class<T> collectionClass, TypeTag tag, Function<Object[], T> createFn,
            Function<T, ISeq> flatten) {
    };

    public static final LiteralDescriptor<PersistentMap> MAP_DESCRIPTOR = new LiteralDescriptor<>(PersistentMap.class,
            TypeTag.MAP, PersistentMap::create, CollectionLiteralForm::flattenMap);

    public static final LiteralDescriptor<PersistentVector> VEC_DESCRIPTOR = new LiteralDescriptor<>(
            PersistentVector.class, TypeTag.VEC, PersistentVector::create, PersistentVector::seq);

    public static final LiteralDescriptor<PersistentList> LIST_DESCRIPTOR = new LiteralDescriptor<>(
            PersistentList.class, TypeTag.SEXP, PersistentList::reversed, l -> seq(Helpers.toList(l)));

    public static final LiteralDescriptor<PersistentSet> SET_DESCRIPTOR = new LiteralDescriptor<>(PersistentSet.class,
            TypeTag.SET, PersistentSet::createArr, PersistentSet::seq);

    private static Map<TypeTag, LiteralDescriptor<?>> TAGGED_LITERAL = new HashMap<>();
    private static Map<Class<?>, LiteralDescriptor<?>> CLASS_LITERAL = new HashMap<>();
    
    static {
        putMeta(MAP_DESCRIPTOR);
        putMeta(VEC_DESCRIPTOR);
        putMeta(LIST_DESCRIPTOR);
        putMeta(SET_DESCRIPTOR);
    }
    
    private static void putMeta(LiteralDescriptor<?> d) {
        TAGGED_LITERAL.put(d.tag(), d);
        CLASS_LITERAL.put(d.collectionClass(), d);
    }
    
    public static <T> LiteralDescriptor<T> getCollectionDescriptor(Class<T> clazz) {
        return (LiteralDescriptor<T>) CLASS_LITERAL.get(clazz);
    }
    
    public static LiteralDescriptor<?> getTag(TypeTag tag) {
        return TAGGED_LITERAL.get(tag);
    }
    

    private static ISeq flattenMap(PersistentMap map) {
        if (map.count() == 0) {
            return ISeq.EMPTY;
        }
        return map.seq().flatMap(eraw -> {
            Entry e = (Entry) eraw;
            return ISeq.of(e.getKey(), e.getValue());
        });
    }


    private final TypeTag clType;

    private final T form;

    private final Function<Object[], T> create;

    private final LiteralDescriptor<T> desc;

    private final Class<?> collectionClass;

    public CollectionLiteralForm(TypeTag clType, T form, LiteralDescriptor<T> desc) {
        super();
        this.clType = clType;
        this.collectionClass = desc.collectionClass();
        this.form = form;
        this.create = desc.createFn();
        this.desc = desc;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        String methodName = clType.name().toLowerCase();
        final List<DeferredCompilation> parts = new ArrayList<>();
        ISeq seq = desc.flatten().apply(form);
        for (Object o : ISeq.iter(seq)) {
            DeferredCompilation defer = Compiler.compileDefer(compilerState, o);
            parts.add(defer);
        }
    
        final Optional<Object> condyForm = createCondy(methodName, parts);
        return createDefComp(parts, methodName, condyForm);
    }

    @Override
    public DeferredCompilation macroCompileForm(CompilerState compilerState, Keyword context) {

        return new DeferredCompilation(clType, null, cs -> {
            List<DeferredCompilation> defList = new ArrayList<>();
            
            long mask = 0;
            int shift = 1;
            int count = 0;
            
            for (Object o : ISeq.iter(desc.flatten().apply(form))) {                
                DeferredCompilation defer = Compiler.macroCompileDefer(cs, o, context);
                
                defList.add(defer);
                ++count;
                if (defer.formType() == TypeTag.SEXP && 
                        defer.ref() == IntrinsicBinding.UNQUOTE_SPLICE) {
                    mask |= shift;
                }
                shift <<= 1;
            }
            // Actual compile
            for (var dc : defList) {
                dc.compile().accept(cs);
            }
            
            // TODO this size right?
            List<TypeRecord> args = cs.getMethodStack().popN(count); 
            
            indy(cs.getCurrentMethodVisitor(), "bootstrap", SExpr.class, desc.collectionClass(), getJavaTypeArray(args), mask);
            
            cs.getMethodStack().push(desc.collectionClass());
        });
    }

    @Override
    public T evaluateForm(CompilerState cs) throws Throwable {
        List<Object> args = Compiler.evaluateArgs(cs, desc.flatten().apply(form));
        return create.apply(args.toArray());
    }

    @Override
    public MacroEvaluated macroEvaluateForm(CompilerState cs, Keyword context) throws Throwable {
        List<Object> args = Compiler.macroEvaluateArgs(cs, desc.flatten().apply(form), context);
        T map = create.apply(args.toArray());
        return new MacroEvaluated(map, false);
    }

    private Optional<Object> createCondy(String methodName, final List<DeferredCompilation> parts) {
        List<Object> forms = new ArrayList<>();
    
        for (var part : parts) {
            if (part.ldcForm().isPresent()) {
                forms.add(part.ldcForm().get());
            } else {
                return Optional.empty();
            }
        }
    
        ConstantDynamic cform = new ConstantDynamic(methodName, getDescriptor(collectionClass), CONDY_BOOTSTRAP_HANDLE,
                forms.toArray());
        return Optional.of(cform);
    }

    private DeferredCompilation createDefComp(final List<DeferredCompilation> parts, String methodName,
            final Optional<Object> condyForm) {
        return new DeferredCompilation(clType, null, condyForm, (cs) -> {
            MethodVisitor mv = cs.getCurrentMethodVisitor();
            boolean isConstant = false;
            if (condyForm.isPresent()) {
                // Use condy
                mv.visitLdcInsn(condyForm.get());
                isConstant = true;
            } else {
                // Create recipe based on a mix of constants/stack args
                StringBuilder recipe = new StringBuilder();
                List<Object> indyArgs = new ArrayList<>();
                indyArgs.add(null); // recipe slot
                int argCount = 0;
                for (DeferredCompilation part : parts) {
                    Optional<Object> constForm = part.ldcForm();
                    if (constForm.isPresent()) {
                        indyArgs.add(constForm.get());
                        recipe.append(PersistentLiteralLinker.CONSTANT);
                    } else {
                        part.compile().accept(cs);
                        argCount++;
                        recipe.append(PersistentLiteralLinker.ORDINARY);
                    }
                }
    
                MethodStack stack = cs.getMethodStack();
                List<TypeRecord> args = stack.popN(argCount);
    
                indyArgs.set(0, recipe.toString());
    
                mv.visitInvokeDynamicInsn(methodName,
                        Type.getMethodDescriptor(Type.getType(collectionClass), getJavaTypeArray(args)),
                        LITERAL_BOOTSTRAP_HANDLE, indyArgs.toArray());
            }
            cs.getMethodStack().push(collectionClass, isConstant);
        });
    }

}
