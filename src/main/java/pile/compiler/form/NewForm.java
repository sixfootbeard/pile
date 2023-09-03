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

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodStack;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.typed.DynamicTypeLookup;
import pile.compiler.typed.TypeMatcher;
import pile.compiler.typed.TypedHelpers;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileException;
import pile.core.exception.PileExecutionException;
import pile.core.indy.InteropLinker;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.util.CollectionUtils;

public class NewForm implements Form {

    private final PersistentList form;
    private final Namespace ns;

    public NewForm(PersistentList form) {
        this.ns = NAMESPACE.getValue();
        this.form = form;
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.NEW, this::compile);

    }

    private void compile(CompilerState cs) {
        var sym = expectSymbol(second(form));
        Class<?> clazz;
        try {
            clazz = sym.getAsClass(ns);
        } catch (PileException e) {
            throw new PileCompileException("Could not resolve symbol to a class:" + sym, LexicalEnvironment.extract(form), e);
        }
        
        ISeq args = nnext(form);

        List<TypeRecord> compileArgs = Compiler.compileArgs(cs, args);
        long anyMask = getAnyMask(compileArgs);

        indy(cs.getCurrentMethodVisitor(), "constructor", InteropLinker.class, "constructor", clazz,
                getJavaTypeArray(compileArgs), anyMask, clazz.getName());
        cs.getMethodStack().push(clazz);
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        // (new class-sym arg0 arg1...)

        Lookup lookup = lookup();

        var sym = expectSymbol(second(form));
        Class<?> clazz;
        try {
            clazz = sym.getAsClass(ns);
        } catch (PileException e) {
            throw new PileExecutionException("Could not resolve symbol to a class:" + sym, LexicalEnvironment.extract(form), e);
        }

        ISeq args = nnext(form);
        List collected = Compiler.evaluateArgs(cs, args);
        List<Class<?>> argClasses = Helpers.getArgClasses(collected);
        MethodType methodType = methodType(clazz, argClasses);        
        MethodHandle cons = InteropLinker.findConstructor(lookup, clazz, argClasses);

        try {
            return cons.asType(methodType).invokeWithArguments(collected);
        } catch (Throwable e) {
            throw new PileExecutionException("Cannot call constructor: " + cons, LexicalEnvironment.extract(form), e);
        }
    }

}
