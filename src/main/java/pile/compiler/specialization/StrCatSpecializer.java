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
package pile.compiler.specialization;

import static org.objectweb.asm.Opcodes.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.parse.TypeTag;

/**
 * Specializes a str cat form using {@link StringConcatFactory}. <br>
 * <br>
 * eg. Normally a call to (str "a" "b") would compile down to call
 * pile.core/str, but if we determine that the user is calling
 * (pile.core/str...) we specialize this compilation into a indy form targeting
 * {@link StringConcatFactory} bootstraps. We have to leave the library method
 * in because it needs to be available in instances when we can't see the
 * target:
 * 
 * <pre>
 * (def cat (fn* [f a b] (f a b)))
 * (def docat (fn* [] (cat str "a" "b")))
 * </pre>
 *
 */
public class StrCatSpecializer implements FormSpecialization {

	private static final Logger LOG = LoggerSupplier.getLogger(StrCatSpecializer.class);
	
	public static final Symbol STR_FN = new Symbol("pile.core", "str");

	private static final Handle WITH_CONSTANTS = new Handle(H_INVOKESTATIC,
			Type.getType(StringConcatFactory.class).getInternalName(), "makeConcatWithConstants",
			getBootstrapDescriptor(STRING_TYPE, OBJECT_ARRAY_TYPE), false);

	private static final String ORDINARY = "\1";
	private static final String CONSTANT = "\2";

	private final PersistentList form;
	private final Namespace ns;

	public StrCatSpecializer(PersistentList form) {
		this.ns = NAMESPACE.getValue();
		this.form = form;
	}

	@Override
	public boolean specialize(CompilerState cs) {

		GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();

		StringBuilder recipe = new StringBuilder();

		ISeq formParts = next(form);

		boolean anyConst = false;
		for (Object part : formParts) {
			if (isConstable(Compiler.compileDefer(cs, part))) {
				anyConst = true;
			}
		}
		
		if (anyConst) {
			List<Object> indyArgs = new ArrayList<>();
			indyArgs.add(null); // Slot for recipe
			int ordinaryCount = 0;
			for (Object part : formParts) {
				ordinaryCount = addPart(cs, recipe, indyArgs, ordinaryCount, part);
			}

			MethodStack methodStack = cs.getMethodStack();
			List<TypeRecord> popN = methodStack.popN(ordinaryCount);
			Type[] args = getJavaTypeArray(popN);

			indyArgs.set(0, recipe.toString());
			Object[] indyArgArray = indyArgs.toArray();
			ga.invokeDynamic("strcat", Type.getMethodDescriptor(STRING_TYPE, args), WITH_CONSTANTS, indyArgArray);
			methodStack.push(String.class);
		} else {
			int count = 0;
			for (Object part : formParts) {
				Compiler.compile(cs, part);
				++count;
			}

			MethodStack methodStack = cs.getMethodStack();
			List<TypeRecord> popN = methodStack.popN(count);
			Type[] args = getJavaTypeArray(popN);

			indy(ga, "strcat", StringConcatFactory.class, "makeConcat", String.class, args);
			methodStack.push(String.class);
		}
		return true;
	}

	private int addPart(CompilerState cs, StringBuilder recipe, List<Object> indyArgs, int ordinaryCount, Object part) {
		DeferredCompilation defer = Compiler.compileDefer(cs, part);
		TypeTag type = defer.formType();
		if (type == TypeTag.CHAR) {
			recipe.append(defer.ldcForm().get());
		} else if (type == TypeTag.FALSE) {
			recipe.append(CONSTANT);
			indyArgs.add("false");
		} else if (type == TypeTag.TRUE) {
			recipe.append(CONSTANT);
			indyArgs.add("true");
		} else if (isConstable(defer)) {
			recipe.append(CONSTANT);
			indyArgs.add(defer.ldcForm().get());
		} else if (type == TypeTag.SEXP) {
			PersistentList nested = (PersistentList) part;
			Object first = first(nested);
			TypeTag nestedType = type(first);
			if (nestedType == TypeTag.SYMBOL) {
				ScopeLookupResult slr = cs.getScope().lookupSymbolScope((Symbol) first);
				if (slr != null && slr.namespace().equals("pile.core") && slr.sym().equals("str")) {
					for (Object np : next(nested)) {
						ordinaryCount = addPart(cs, recipe, indyArgs, ordinaryCount, np);
					}
				} else {
					ordinaryCount = addOrdinary(cs, recipe, ordinaryCount, part);
				}
			} else {
				ordinaryCount = addOrdinary(cs, recipe, ordinaryCount, part);
			}
		} else {
			ordinaryCount = addOrdinary(cs, recipe, ordinaryCount, part);
		}
		return ordinaryCount;
	}

	private boolean isConstable(DeferredCompilation defer) {
		return defer.ldcForm().isPresent();
	}

	private int addOrdinary(CompilerState cs, StringBuilder recipe, int ordinaryCount, Object part) {
		recipe.append(ORDINARY);
		Compiler.compile(cs, part);
		++ordinaryCount;
		return ordinaryCount;
	}

}
