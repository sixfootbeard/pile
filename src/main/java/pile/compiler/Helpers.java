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

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Type.getType;
import static pile.util.CollectionUtils.mapA;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.Associative;
import pile.collection.EmptyMap;
import pile.collection.PersistentArrayVector;
import pile.collection.PersistentHashMap;
import pile.collection.PersistentHashSet;
import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentVector;
import pile.collection.SingleMap;
import pile.compiler.MethodStack.InfiniteRecord;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.typed.Any;
import pile.core.Concat;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.Metadata;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.exception.InvariantFailedException;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.exception.ShouldntHappenException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;
import pile.core.parse.TypeTag;
import pile.nativebase.NativeCore;

public class Helpers {

    private static final Logger LOG = LoggerSupplier.getLogger(Helpers.class);

//	public static final Type BOOLEAN_TYPE = Type.getType(boolean.class);
    public static final Type CALL_SITE_TYPE = Type.getType(CallSite.class);
    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final Type VAR_ARGS_TYPE = Type.getType(ISeq.class);
    public static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
    public static final Type KEYWORD_TYPE = Type.getType(Keyword.class);
    public static final Type STRING_TYPE = Type.getType(String.class);

    public static final Class<?> ANY_CLASS = Any.class;

    public static final Type[] EMPTY_TYPE = new Type[0];

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = new HashMap<>();
    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = new HashMap<>();

    static {
        PRIMITIVE_TO_WRAPPER.put(boolean.class, Boolean.class);
        PRIMITIVE_TO_WRAPPER.put(byte.class, Byte.class);
        PRIMITIVE_TO_WRAPPER.put(char.class, Character.class);
        PRIMITIVE_TO_WRAPPER.put(double.class, Double.class);
        PRIMITIVE_TO_WRAPPER.put(float.class, Float.class);
        PRIMITIVE_TO_WRAPPER.put(int.class, Integer.class);
        PRIMITIVE_TO_WRAPPER.put(long.class, Long.class);
        PRIMITIVE_TO_WRAPPER.put(short.class, Short.class);
        PRIMITIVE_TO_WRAPPER.put(void.class, Void.class);

        // ugh
        PRIMITIVE_TO_WRAPPER.entrySet().stream()
                .forEach(entry -> WRAPPER_TO_PRIMITIVE.put(entry.getValue(), entry.getKey()));
    }

    public static Class<?> wrapperToPrimitive(Class<?> clazz) {
        return WRAPPER_TO_PRIMITIVE.get(clazz);
    }

    public static Class<?> toPrimitive(Class<?> clazz) {
        return WRAPPER_TO_PRIMITIVE.getOrDefault(clazz, clazz);
    }

    public static Class<?> primitiveToWrapper(Class<?> clazz) {
        return PRIMITIVE_TO_WRAPPER.get(clazz);
    }

    public static Class<?> toWrapper(Class<?> clazz) {
        return PRIMITIVE_TO_WRAPPER.getOrDefault(clazz, clazz);
    }

    public static boolean isPrimitive(Class<?> clazz) {
        return clazz.isPrimitive();
    }

    public static boolean isReference(Class<?> clazz) {
        return !isPrimitive(clazz);
    }

    public static boolean ifCheck(Object o) {
        return switch (o) {
            case null -> false;
            case Boolean bool -> bool;
            default -> true;
        };
    }

    // Helpers
    
    public interface Validator<T> { public T validate(Object o); }
    
    public static Validator<Symbol> IS_SYMBOL = validateClass(Symbol.class, "Expected Symbol, found %s");
    public static Validator<PersistentVector> IS_VECTOR = validateClass(PersistentVector.class, "Expected Vector, found %s");
    

    private static <T> Validator<T> validateClass(Class<T> clazz, String msg) {
        return (o) -> {
            Class<? extends Object> actualClass = o.getClass();
            try {
                return clazz.cast(o);
            } catch (ClassCastException e) {
                String emsg = String.format(msg,  actualClass.getName());
                throw new PileSyntaxErrorException(emsg, LexicalEnvironment.extract(o));
            }             
        };
    }    
    
    public static <T> T expect(Object o, Validator<T> validator) {
        return validator.validate(o);
    }
    
    public static <T> T expect(Object o, Validator<T> validator, String msg) {
        try {
            return validator.validate(o);
        } catch (PileSyntaxErrorException e) {
            throw new PileSyntaxErrorException(msg, e);
        }
    }


    public static Symbol expectSymbol(Object o) {
        expectType(o, TypeTag.SYMBOL);
        return (Symbol) o;
    }

    public static Symbol toSymbol(String o) {
        return new Symbol(o);
    }

    public static Metadata expectMeta(Object form) {
        if (form instanceof Metadata meta) {
            return meta;
        }
        throw error("Expected metadata, found: " + form.getClass());
    }

    public static Keyword expectKeyword(Object list) {
        if (list instanceof Keyword k) {
            return k;
        }
        throw error("Expected keyword, found: " + list);
    }

    public static PersistentList expectList(Object list) {
        if (list instanceof PersistentList pl) {
            return pl;
        }
        throw error("Expected list, found (" + list.getClass() + "): " + list);
    }

    public static PersistentList toList(Object o) {
        return switch (o) {
            case null -> throw error("Cannot convert null to list");
            case PersistentList pl -> pl;
            case ISeq seq -> PersistentList.fromSeq(seq);
            default -> throw error("Bad list type");
        };        
    }

    public static PersistentVector expectVector(Object list) {
        expectType(list, TypeTag.VEC);
        if (list instanceof PersistentVector pl) {
            return pl;
        }
        throw error("Expected vector, found: " + list);
    }

    public static void expectType(Object list, TypeTag type) {
        TypeTag foundtype = getTag(list);
        if (foundtype == type) {
            return;
        }
        throw error("Expected type=" + type + ", found type=" + foundtype);

    }

    public static boolean isKeywordTrue(Associative assoc, Keyword key) {
        return (boolean) assoc.get(key, false);
    }

    public static RuntimeException notYetImplemented(String string) {
        return new RuntimeException(string);
    }

    public static RuntimeException notYetImplemented(String string, Throwable t) {
        return new RuntimeException(string, t);
    }

    public static RuntimeException error(String string) {
        return new RuntimeException(string);
    }

    public static RuntimeException error(String string, Throwable t) {
        return new RuntimeException(string, t);
    }

    public static RuntimeException shouldNotHappen() {
        throw new ShouldntHappenException();
    }

    public static RuntimeException shouldNotHappen(String s) {
        throw new ShouldntHappenException(s);
    }

    public static RuntimeException shouldNotHappen(String s, Throwable t) {
        throw new ShouldntHappenException(s, t);
    }

    public static RuntimeException shouldNotHappen(Throwable t) {
        throw new ShouldntHappenException(t);
    }

    public static TypeTag type(Object first) {
        return getTag(first);
    }

    public static String str(Object o) {
        TypeTag typeTag = getTag(o);
        if (typeTag.equals(TypeTag.STRING)) {
            return (String) o;
        }
        throw error("Expected string literal, found: " + o);
    }

    static final Map<Class<?>, TypeTag> TAG_CLASSES = new HashMap<>();
    static {
        TAG_CLASSES.put(Symbol.class, TypeTag.SYMBOL);

        TAG_CLASSES.put(Pattern.class, TypeTag.REGEX);
        TAG_CLASSES.put(DeferredRegex.class, TypeTag.REGEX);
//		TAG_CLASSES.put(Number.class, TypeTag.NUMBER);
        TAG_CLASSES.put(Character.class, TypeTag.CHAR);
        TAG_CLASSES.put(Keyword.class, TypeTag.KEYWORD);
        TAG_CLASSES.put(String.class, TypeTag.STRING);

        // Collections
        TAG_CLASSES.put(PersistentArrayVector.class, TypeTag.VEC);
        TAG_CLASSES.put(PersistentHashMap.class, TypeTag.MAP);
        TAG_CLASSES.put(SingleMap.class, TypeTag.MAP);
        TAG_CLASSES.put(EmptyMap.class, TypeTag.MAP);
        TAG_CLASSES.put(PersistentHashSet.class, TypeTag.SET);
        TAG_CLASSES.put(PersistentList.class, TypeTag.SEXP);

    }

    public static TypeTag getTag(Object lit) {
        if (lit == null) {
            return TypeTag.NIL;
        }

        Class<? extends Object> clazz = lit.getClass();
        TypeTag maybeTag = TAG_CLASSES.get(clazz);
        if (maybeTag != null) {
            return maybeTag;
        }
        if (lit instanceof ISeq iseq) {
            // FIXME This gets converted later back into a PList
            return TypeTag.SEXP;
        }
        if (Number.class.isAssignableFrom(clazz)) {
            return TypeTag.NUMBER;
        }

        if (lit instanceof Boolean b) {
            if (b) {
                return TypeTag.TRUE;
            } else {
                return TypeTag.FALSE;
            }
        }

        throw new PileInternalException("Invalid tag class: " + clazz + ": " + lit);
    }

    public static String strSym(Object first) {
        TypeTag typeTag = getTag(first);
        if (typeTag.equals(TypeTag.SYMBOL)) {
            return ((Symbol) first).getName();
        }
        if (typeTag != TypeTag.SEXP) {
            return (String) first;
        }
        throw error("Expected string literal, found: " + first);
    }

    public static void ensureSize(String name, PersistentList form, int size) {
        if (form.count() != size) {
            throw Helpers.error("Unexpected size: " + form.count() + ", expected : " + size + " for " + name);
        }
    }

    public static Number number(Object arg) {
        expectType(arg, TypeTag.NUMBER);
        return (Number) arg;
    }

    public static Type[] getObjectTypeArray(Class<?> base, int sizes) {
        Type[] args = new Type[sizes + 1];
        args[0] = Type.getType(base);
        for (int i = 0; i < sizes; ++i) {
            args[i + 1] = Helpers.OBJECT_TYPE;
        }
        return args;
    }

    public static Type[] getTypeArray(Type base, List<TypeRecord> classes) {
        int sizes = classes.size();
        Type[] args = new Type[sizes + 1];

        args[0] = base;
        var converted = getJavaTypeArray(classes);
        System.arraycopy(converted, 0, args, 1, converted.length);
        return args;
    }

    public static Type[] getJavaTypeArray(List<TypeRecord> classes) {
        return mapA(classes, c -> Type.getType(c.javaClass()), Type[]::new);
    }

    public static Class[] getJavaClassArray(List<TypeRecord> classes) {
        return mapA(classes, TypeRecord::clazz, Class[]::new);
    }

    public static Type[] getObjectTypeArray(int sizes) {
        return Collections.nCopies(sizes, Helpers.OBJECT_TYPE).toArray(Type[]::new);
    }

    public static MethodType getMethodTypeFromArgs(Object[] args) {
        List<Class<?>> typeArray = new ArrayList<>();
        Arrays.stream(args).map(o -> o == null ? Object.class : o.getClass()).forEach(typeArray::add);
        MethodType type = methodType(Object.class, typeArray);
        return type;
    }

    public static Type[] getVarArgsTypeArray(int sizes) {
        Type[] args = new Type[sizes + 1];
        int i = 0;
        for (; i < sizes; ++i) {
            args[i] = Helpers.OBJECT_TYPE;
        }
        args[i] = Helpers.VAR_ARGS_TYPE;
        return args;
    }

//	getVarArgsTypeArray

    public static String getBootstrapDescriptor(Type... extra) {

        Type[] types = new Type[3 + extra.length];

        types[0] = Type.getType(Lookup.class);
        types[1] = STRING_TYPE;
        types[2] = Type.getType(MethodType.class);
        System.arraycopy(extra, 0, types, 3, extra.length);
        ;

        return Type.getMethodDescriptor(CALL_SITE_TYPE, types);
    }

    public static String getConstantBootstrapDescriptor(Type returnType, Type... extra) {

        Type[] types = new Type[3 + extra.length];

        types[0] = Type.getType(Lookup.class);
        types[1] = STRING_TYPE;
        types[2] = Type.getType(Class.class);
        System.arraycopy(extra, 0, types, 3, extra.length);
        ;

        return Type.getMethodDescriptor(returnType, types);
    }

    public static String getConstantBootstrapDescriptor(Class<?> clazz, Type... extra) {
        return getConstantBootstrapDescriptor(Type.getType(clazz), extra);
    }

    public static MethodType createClassArray(Object[] args) {
        List<Class<?>> clazz = new ArrayList<>();

        for (Object o : args) {
            clazz.add(o == null ? Object.class : o.getClass());
        }

        return methodType(Object.class, clazz);
    }

    public static Type[] types(List<Class<?>> typeArray) {
        List<Type> types = typeArray.stream().map(Type::getType).collect(Collectors.toList());
        return types.toArray(new Type[types.size()]);
    }

    public static boolean is(Method m, int f) {
        return (m.getModifiers() & f) > 0;
    }

    public static List<Class<?>> getArgClasses(List<?> args) {
        List<Class<?>> typeArray = new ArrayList<>(args.size());
        for (Object o : args) {
            typeArray.add(o == null ? Void.class : o.getClass());
        }
        return typeArray;
    }
    
    public static List<Class<?>> getArgClasses(Object[] args) {
        return getArgClasses(Arrays.asList(args));
    }

    /**
     * Create a method handle matching the provided callsite type which will simply
     * throw the provided exception.
     * 
     * @param <E>
     * @param type
     * @param exType
     * @param cons
     * @param message
     * @return
     */
    public static <E extends Exception> MethodHandle getExceptionHandle(MethodType type, Class<E> exType,
            Function<String, E> cons, String message) {
        MethodHandle ex = MethodHandles.throwException(type.returnType(), exType);
        ex = ex.bindTo(cons.apply(message));
        ex = dropArguments(ex, 0, type.parameterArray());
        return ex;
    }

    public static MethodHandle getExceptionHandle(Class<?> returnType, List<Class<?>> classes) {
        MethodHandle ex = MethodHandles.throwException(returnType, RuntimeException.class);
        ex = ex.bindTo(new RuntimeException("Cannot link to unknown callable type receiver : " + classes.get(0)));
        ex = dropArguments(ex, 0, classes);
        return ex;
    }

    public static void ensure(boolean b, Supplier<String> msg) {
        if (!b) {
            throw new RuntimeException(msg.get());
        }
    }

    public static void ensure(boolean b, String msg) {
        if (!b) {
            throw new RuntimeException(msg);
        }
    }

    public static void ensureEx(boolean b, Function<String, RuntimeException> fn, String msg) {
        if (!b) {
            throw fn.apply(msg);
        }
    }

    public static void ensureEx(boolean b, Function<String, RuntimeException> fn, Supplier<String> sup) {
        if (!b) {
            throw fn.apply(sup.get());
        }
    }

    /**
     * Ensures provided boolean returns true, otherwise extracts a
     * {@link LexicalEnvironment} from the provided form (if any) and throws a
     * {@link PileSyntaxErrorException} with the provided message.
     * 
     * @param b
     * @param form
     * @param msg
     */
    public static void ensureSyntax(boolean b, Object form, String msg) {
        if (!b) {
            Optional<LexicalEnvironment> lex = LexicalEnvironment.extract(form);
            throw new PileSyntaxErrorException(msg, lex);
        }
    }
    
    /**
     * Ensures provided boolean returns true, otherwise extracts a
     * {@link LexicalEnvironment} from the provided form (if any) and throws a
     * {@link PileSyntaxErrorException} with the supplied message.
     * 
     * @param b
     * @param form
     * @param msg
     */
    public static void ensureSyntax(boolean b, Object form, Supplier<String> msg) {
        if (!b) {
            Optional<LexicalEnvironment> lex = LexicalEnvironment.extract(form);
            throw new PileSyntaxErrorException(msg.get(), lex);
        }
    }
    
    /**
     * Ensures provided boolean returns true, otherwise extracts a
     * {@link LexicalEnvironment} from the provided form (if any) and throws a
     * {@link PileCompileException} with the provided message.
     * 
     * @param b
     * @param form
     * @param msg
     */
    public static void ensureCompile(boolean b, Object form, String msg) {
        if (!b) {
            Optional<LexicalEnvironment> lex = LexicalEnvironment.extract(form);
            throw new PileCompileException(msg, lex);
        }
    }
    
    /**
     * Ensures provided boolean returns true, otherwise extracts a
     * {@link LexicalEnvironment} from the provided form (if any) and throws a
     * {@link PileCompileException} with the supplied message.
     * 
     * @param b
     * @param form
     * @param msg
     */
    public static void ensureCompile(boolean b, Object form, Supplier<String> msg) {
        if (!b) {
            Optional<LexicalEnvironment> lex = LexicalEnvironment.extract(form);
            throw new PileCompileException(msg.get(), lex);
        }
    }
    
    public static Supplier<InvariantFailedException> internalErrorGen(String msg, Object form) {
        return () -> new InvariantFailedException(msg, LexicalEnvironment.extract(form));
    }
    
    public static Supplier<InvariantFailedException> internalErrorGen(String msg) {
        return () -> new InvariantFailedException(msg);
    }

    public static Optional<Class<?>> getTypeHint(Metadata meta, Namespace ns) {
        Class<?> argType = null;
        Object maybeAnnotatedType = ParserConstants.ANNO_TYPE_KEY.call(meta.meta());
        if (maybeAnnotatedType != null) {
            argType = ((Symbol) maybeAnnotatedType).getAsClass(ns);
        }
        return Optional.ofNullable(argType);
    }

    public static MethodType getMethodType(Constructor cons) {
        return methodType(cons.getDeclaringClass(), cons.getParameterTypes());
    }

    public static ISeq<?> concat(Object lhs, Object rhs) {
        var l = NativeCore.seq(lhs);
        if (l == ISeq.EMPTY) {
            return NativeCore.seq(rhs);
        } else {
            var sup = StableValue.supplier(() -> NativeCore.seq(rhs));
            return new Concat(l, sup);
        }
    }

    public static <T> Optional<T> single(Iterator<T> it) {
        if (!it.hasNext()) {
            return Optional.empty();
        }
        T t = it.next();
        if (it.hasNext()) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    public static long getAnyMask(List<TypeRecord> classes) {
        ensure(classes.size() < 64, "Too many classes to many Any mask for");
        long mask = 0;
        int i = 0;
        for (TypeRecord typeRecord : classes) {
            if (typeRecord.clazz() == Any.class) {
                mask |= (1 << i);
            }
            ++i;
        }
        return mask;
    }

    public static List<Class<?>> blendAnyMask(MethodType type, long anyMask) {
        return blendAnyMaskType(type, anyMask).parameterList();
    }

    public static MethodType blendAnyMaskType(MethodType type, long anyMask) {
        if (anyMask == 0) {
            return type;
        }
        int i = 0;
        while (i < type.parameterCount()) {
            if (((1 << i) & anyMask) != 0) {
                type = type.changeParameterType(i, Any.class);
            }
            ++i;
        }
        return type;
    }

    public static <T> List<T> withoutHead(List<T> input) {
        return new ArrayList<>(input.subList(1, input.size()));
    }

    public static Class toCompilableType(Class cls) {
        return Any.class.equals(cls) ? Object.class : cls;
    }

    public static Class<?> getSafeClass(Object o) {
        return o == null ? Object.class : o.getClass();
    }

    /**
     * Generate instructions to convert class 'from' to class 'to'. 
     * @param ga
     * @param from
     * @param to
     */
    public static void coerce(GeneratorAdapter ga, Class<?> from, Class<?> to) {
        if (from.isPrimitive()) {
            if (to.isPrimitive()) {
                ga.cast(getType(from), getType(to));
            } else {
                // from prim / to ref
                ga.box(getType(from));
                ga.checkCast(getType(to));
            }
        } else {
            if (to.isPrimitive()) {
                Class<?> primitiveToWrapper = primitiveToWrapper(to);
                // from ref / to prim

                // numbers
                // char
                // byte
                // boolean
                throw new UnsupportedOperationException("ref -> prim coercions. [from=" + from + ", to=" + to + "]");

            } else {
                // from ref / to ref
                if (!Object.class.equals(to)) {
                    ga.checkCast(getType(to));
                }
            }
        }
    }
    
    public static Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Helpers.class.getClassLoader());
    }
    
    public static <T> Map<T, Integer> indexMap(Iterable<T> it) {
        Map<T, Integer> out = new HashMap<>();
        int i = 0;
        for (T t : it) {
            out.put(t, i);
            ++i;
        }
        return out;
    }

    public static void handleLineNumber(MethodVisitor mv, Object o) {
        if (o instanceof Metadata m) {
            PersistentMap meta = m.meta();
            Object maybeLn = meta.get(ParserConstants.LINE_NUMBER_KEY);
            if (maybeLn != null) {
                Label l = new Label();
                mv.visitLabel(l);
                int lineNumber = (Integer) maybeLn;
                mv.visitLineNumber(lineNumber, l);
            }
        }
    }

    public static Class<?> findParentType(Class<?> lhs, Class<?> rhs) {
        return findParentTypeOpt(lhs, rhs).orElse(Object.class);
    }

    public static Optional<Class<?>> findParentTypeOpt(Class<?> lhs, Class<?> rhs) {
        if (lhs.equals(rhs)) {
            return Optional.of(lhs);
        }

        boolean lsr = lhs.isAssignableFrom(rhs);
        boolean rsl = rhs.isAssignableFrom(lhs);
        if (lsr) {
            return Optional.of(lhs);
        } else if (rsl) {
            return Optional.of(rhs);
        } else {
            return Optional.empty();
        }
    }

    public static <T> T matchArity(Map<Integer, T> arities, int varArgsArity, T varArgsImpl, int size) {
        if (arities.containsKey(size)) {
            return arities.get(size);
        }

        if (varArgsArity != -1) {
            if (varArgsArity < size) {
                return varArgsImpl;
            }
        }
        return null;
    }

    /**
     * Pop a stack record, ensuring it is not an {@link InfiniteRecord}.
     * 
     * @param stack  The stack to pop
     * @param syntax The syntax that produced the corresponding stack record to use
     *               in event of an error for a {@link LexicalEnvironment}.
     * @param errMsg The error message if the stack element is an
     *               {@link InfiniteRecord}.
     * @return The stack {@link TypeRecord}.
     * @throws PileCompileException If the top stack record is an
     *                              {@link InfiniteRecord}.
     */
    public static MethodStack.TypeRecord popNoInfinite(MethodStack stack, Object syntax, String errMsg) {
        MethodStack.StackRecord sr = stack.popR();
        return switch (sr) {
            case MethodStack.TypeRecord tr -> tr;
            case MethodStack.InfiniteRecord _ -> throw new PileCompileException(errMsg, LexicalEnvironment.extract(syntax));
        };
    }

    /**
     * {@link ScopedValue} orElse which can actually take null.
     * 
     * @param <T>
     * @param sv
     * @param orElse
     * @return
     */
    public static <T> T orElse(ScopedValue<T> sv, T orElse) {
        return sv.isBound() ? sv.get() : orElse;
    }
}
