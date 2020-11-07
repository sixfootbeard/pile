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
package pile.core.indy;

import static java.lang.invoke.MethodHandles.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.collection.PersistentSet;
import pile.collection.PersistentVector;
import pile.compiler.form.NullForm;
import pile.core.exception.PileInternalException;
import pile.collection.PersistentArrayVector;
import pile.util.ConstantDynamicBootstrap;
import pile.util.InvokeDynamicBootstrap;

/**
 * Bootstrap targets (indy and condy) for persistent collection literals in
 * code.
 *
 */
public class PersistentLiteralLinker {

	private static final MethodHandle VECTOR_LITERAL_METHOD;
	private static final MethodHandle MAP_LITERAL_METHOD;
	private static final MethodHandle LIST_LITERAL_METHOD;
	private static final MethodHandle SET_LITERAL_METHOD;
	private static final MethodHandle REVERSE_LIST_LITERAL_METHOD;

	public static final char ORDINARY = '\1';
	public static final char CONSTANT = '\2';

	static {
		try {
			Lookup lookup = MethodHandles.lookup();
			VECTOR_LITERAL_METHOD = lookup.findStatic(PersistentVector.class, "createArr",
					MethodType.methodType(PersistentVector.class, Object[].class));
			MAP_LITERAL_METHOD = lookup.findStatic(PersistentMap.class, "createArr",
					MethodType.methodType(PersistentMap.class, Object[].class));
			LIST_LITERAL_METHOD = lookup.findStatic(PersistentList.class, "createArr",
					MethodType.methodType(PersistentList.class, Object[].class));
			SET_LITERAL_METHOD = lookup.findStatic(PersistentSet.class, "createArr",
					MethodType.methodType(PersistentSet.class, Object[].class));
			REVERSE_LIST_LITERAL_METHOD = lookup.findStatic(PersistentList.class, "reversed",
					MethodType.methodType(PersistentList.class, Object[].class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new PileInternalException(e);
		}
	}

	private static final Map<String, MethodHandle> CONSTRUCTOR_HANDLES = Map.of(
	        "vec", VECTOR_LITERAL_METHOD, 
	        "map", MAP_LITERAL_METHOD, 
			"set", SET_LITERAL_METHOD, 
			"list", LIST_LITERAL_METHOD, 
			"reverseList", REVERSE_LIST_LITERAL_METHOD);

	private static final Map<String, Function<Object[], Object>> FROM_ARRAY = Map.of(
	        "vec", PersistentVector::createArr,
			"map", PersistentMap::createArr, 
			"set", PersistentSet::createArr, 
			"list", PersistentList::createArr);

	// Optimized recipe forms (optional)
	private static final Map<String, BiFunction<String, Object[], MethodHandle>> RECIPE_HANDLE = Map.of(
	        "vec", PersistentVector::fromRecipe);
	// TODO Optimzied map recipe
	// TODO Optimized set recipe
	
	
    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String name, MethodType type) throws Throwable {
        return new ConstantCallSite(CONSTRUCTOR_HANDLES.get(name).asType(type));
    }

	/**
	 * 
	 * @param caller
	 * @param name      One of "vec", "map", "set", "list", "reverseList"
	 * @param type
	 * @param recipe    The constants recipe form (similar to
	 *                  {@link StringConcatFactory})
	 * @param constants The list of compile time constants.
	 * @return A callsite which will produce the specified persistent collection.
	 * @throws Throwable
	 */
	@InvokeDynamicBootstrap
	public static CallSite bootstrap(Lookup caller, String name, MethodType type, String recipe, Object... constants)
			throws Throwable {
		BiFunction<String, Object[], MethodHandle> recipeFn = RECIPE_HANDLE.get(name);
		MethodHandle handle;
		if (recipeFn == null || constants.length == 0) {
			// If no optimized recipe form or no constants, use default constructor
			MethodHandle cons = CONSTRUCTOR_HANDLES.get(name);
			cons = cons.asCollector(Object[].class, recipe.length());
			int constantIndex = 0;
			int handleIndex = 0;
			for (int i = 0; i < recipe.length(); ++i) {
				char c = recipe.charAt(i);
				if (c == CONSTANT) {
					cons = insertArguments(cons, handleIndex, constants[constantIndex++]);
				} else {
				    handleIndex++;
				}
			}
			handle = cons;
		} else {
			// use recipe form
			handle = recipeFn.apply(recipe, constants);
		}
		if (handle.type().parameterCount() == 0) {
			// If there are no actual args left then just call the constructor since these
			// are all persistent collections.
			Object coll = handle.invokeExact();
			return new ConstantCallSite(constant(coll.getClass(), coll));
		} else {
			return new ConstantCallSite(handle.asType(type));
		}
	}

	/**
	 * Condy bootstrap if all parts are constant.
	 * 
	 * @param lookup
	 * @param name   One of "vec", "map", "set", "list"
	 * @param clazz  The persistent collection class
	 * @param parts  The constant parts
	 * @return An instance of the persistent collection.
	 */
	@ConstantDynamicBootstrap
	public static Object bootstrap(Lookup lookup, String name, Class<Object> clazz, Object... parts) {
		Function<Object[], Object> fn = FROM_ARRAY.get(name);
		if (fn == null) {
			throw new PileInternalException("Unexpected literal type: " + name);
		}
		Object col = fn.apply(parts);
		return col;
	}
}
