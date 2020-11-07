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

import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.objectweb.asm.Type;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentList;
import pile.collection.PersistentMap;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.typed.Any;
import pile.collection.PersistentArrayVector;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.Symbol;
import pile.core.exception.PileSyntaxErrorException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;

/**
 * Parses syntax forms representing a parameter list
 *
 */
public class ParameterParser {

    public record MethodParameter(String name, Class type) {

        public String getCompilableTypeDescriptor() {
            return Type.getDescriptor(getCompilableClass());
        }

        public Type getCompilableType() {
            return Type.getType(getCompilableClass());
        }

        public Class<?> getCompilableClass() {
            return toCompilableType(type);
        }

    };

    public record ParameterList(List<MethodParameter> args, Map<String, Integer> indexes, boolean isVarArgs,
            boolean isJavaVarArgs) {

        /**
         * Update the types with the receiver and types from the supplied method.
         * 
         * @param method
         * @return
         */
        public ParameterList updateTypesAndReceiver(Method method) {
            ensure(args.size() == method.getParameterCount() + 1, "Wrong number of args to update");
            ensure((method.getModifiers() & Modifier.STATIC) == 0, "Must be an instance method");

            List<MethodParameter> newArgs = new ArrayList<>();
            Iterator<MethodParameter> it = args.iterator();
            newArgs.add(new MethodParameter(it.next().name(), method.getDeclaringClass()));

            Class<?>[] parameterTypes = method.getParameterTypes();
            int i = 0;
            while (it.hasNext()) {
                MethodParameter argRecord = it.next();
                newArgs.add(new MethodParameter(argRecord.name(), parameterTypes[i]));
                ++i;
            }
            return new ParameterList(newArgs, indexes, isVarArgs, method.isVarArgs());
        }

        public ParameterList updateTypes(Method method) {
            List<MethodParameter> newArgs = new ArrayList<>();
            Class<?>[] parameterTypes = method.getParameterTypes();
            int i = 0;
            for (MethodParameter argRecord : args) {
                newArgs.add(new MethodParameter(argRecord.name(), parameterTypes[i]));
                ++i;
            }
            return new ParameterList(newArgs, indexes, isVarArgs, method.isVarArgs());
        }

        public ParameterList appendAll(ParameterList suffix) {
            List<MethodParameter> newArgs = new ArrayList<>();
            newArgs.addAll(args);
            newArgs.addAll(suffix.args);

            Map<String, Integer> newIndexes = new HashMap<>();
            newIndexes.putAll(indexes);
            newIndexes.putAll(suffix.indexes);

            return new ParameterList(newArgs, newIndexes, isVarArgs, isJavaVarArgs);
        }

        public ParameterList append(MethodParameter suffix) {
            List<MethodParameter> newArgs = new ArrayList<>();
            newArgs.addAll(args);
            newArgs.add(suffix);

            Map<String, Integer> newIndexes = new HashMap<>();
            newIndexes.putAll(indexes);
            newIndexes.put(suffix.name, newArgs.size());

            return new ParameterList(newArgs, newIndexes, false, false);
        }

        public ParameterList withJavaVarArgs() {
            return withJavaVarArgs(true);
        }

        public ParameterList withJavaVarArgs(boolean isVarArgs) {
            return new ParameterList(args, indexes, isVarArgs, isVarArgs);
        }

        public ParameterList withVarArgs() {
            return new ParameterList(args, indexes, true, isJavaVarArgs);
        }

        public MethodType toMethodType(Class<?> returnType) {
            List<Class<?>> classes = mapL(args(), MethodParameter::getCompilableClass);
            return methodType(returnType, classes);
        }

        public ParameterList append(List<MethodParameter> closureArgs) {
            List<MethodParameter> newArgs = new ArrayList<>(args.size() + closureArgs.size());
            newArgs.addAll(args);
            newArgs.addAll(closureArgs);

            Map<String, Integer> newIndexes = new HashMap<>(indexes);
            int startIdx = args.size();
            for (var cl : closureArgs) {
                newIndexes.put(cl.name(), startIdx);
                ++startIdx;
            }

            return new ParameterList(newArgs, newIndexes, isVarArgs, isJavaVarArgs);
        }

        public MethodParameter lastArg() {
            return args.get(args.size() - 1);
        }

        public ParameterList popFirst() {
            MethodParameter firstArg = args.get(0);
            List<MethodParameter> newArgs = args.subList(1, args.size());
            Map<String, Integer> newIndexes = new HashMap<>(indexes);
            newIndexes.remove(firstArg.name());
            return new ParameterList(newArgs, newIndexes, isVarArgs, isJavaVarArgs);
        }

        public ParameterList popLast() {
            MethodParameter lastArg = args.get(args.size() - 1);
            List<MethodParameter> newArgs = args.subList(0, args.size() - 1);
            Map<String, Integer> newIndexes = new HashMap<>(indexes);
            newIndexes.remove(lastArg.name());
            return new ParameterList(newArgs, newIndexes, isVarArgs, isJavaVarArgs);
        }

        public ParameterList modifyLastArg(UnaryOperator<MethodParameter> fn) {
            MethodParameter last = lastArg();
            MethodParameter newRec = fn.apply(last);
            return this.popLast().append(newRec);
        }

        /**
         * Create a generic compilable version of this method type.
         * 
         * @return
         */
        public ParameterList genericCompilable() {
            List<MethodParameter> recs = new ArrayList<>();
            this.args.forEach(or -> recs.add(new MethodParameter(or.name(), Object.class)));

            ParameterList pr = new ParameterList(recs, indexes, isVarArgs, isJavaVarArgs);

            if (isJavaVarArgs) {
                pr = pr.modifyLastArg(o -> new MethodParameter(o.name(), Object[].class));
            } else if (isVarArgs) {
                pr = pr.modifyLastArg(o -> new MethodParameter(o.name(), ISeq.class));
            }

            return pr;
        }

        /**
         * Create a generic version of this method type.
         * 
         * @return
         */
        public ParameterList generic() {
            List<MethodParameter> recs = new ArrayList<>();
            this.args.forEach(or -> recs.add(new MethodParameter(or.name(), Any.class)));

            ParameterList pr = new ParameterList(recs, indexes, isVarArgs, isJavaVarArgs);

            if (isJavaVarArgs) {
                pr = pr.modifyLastArg(o -> new MethodParameter(o.name(), Any[].class));
            } else if (isVarArgs) {
                pr = pr.modifyLastArg(o -> new MethodParameter(o.name(), ISeq.class));
            }

            return pr;
        }

        public ParameterList prepend(MethodParameter argRecord) {
            List<MethodParameter> newArgs = new ArrayList<>(args.size() + 1);
            newArgs.add(argRecord);
            newArgs.addAll(args);

            Map<String, Integer> newIndexes = new HashMap<>();
            int idx = 0;
            for (var ar : newArgs) {
                newIndexes.put(ar.name(), idx);
                ++idx;
            }

            return new ParameterList(newArgs, newIndexes, isVarArgs, isJavaVarArgs);

        }

        public static ParameterList empty() {
            return new ParameterList(List.of(), Map.of(), false, false);
        }
    };

    private final Iterable form;
    private final Namespace ns;

    public ParameterParser(Namespace ns, Iterable form) {
        super();
        this.form = form;
        this.ns = ns;
    }

    /**
     * Parse the supplied parameter listing, resolving any type tags within the
     * provided namespace.
     * 
     * @return
     */
    public ParameterList parse() {
        Iterable vars = form;

        List<MethodParameter> args = new ArrayList<>();
        Map<String, Integer> indexes = new HashMap<>();

        Iterator it = vars.iterator();
        boolean isVarArgs = false;

        int index = 0;

        // Scope: Add method args
        while (it.hasNext()) {
            Symbol sym = expectSymbol(it.next());

            PersistentMap meta = sym.meta();

            String strSym = sym.getName();

            if (strSym.equals("&")) {
                sym = expectSymbol(it.next());
                strSym = sym.getName();

                MethodParameter arg = new MethodParameter(strSym, ISeq.class);
                indexes.put(strSym, index);
                args.add(arg);

                isVarArgs = true;

                if (it.hasNext()) {
                    throw new PileSyntaxErrorException("Can only have trailing vararg", LexicalEnvironment.extract(form));
                }
                break;
            }

            Class<?> argType = Any.class;
            Class<?> annotatedType = sym.getAnnotatedType(ns);
            if (annotatedType != null) {
                argType = annotatedType;
            }

            MethodParameter arg = new MethodParameter(strSym, argType);
            indexes.put(strSym, index);
            args.add(arg);
            ++index;
        }

        return new ParameterList(args, indexes, isVarArgs, false);
    }

    public static ParameterList noName(List<Class<?>> args) {
        ParameterList base = ParameterList.empty();
        for (int i = 0; i < args.size(); ++i) {
            base = base.append(new MethodParameter("arg" + i, args.get(i)));
        }
        return base;
    }

    public static ParameterList from(Executable m) {
        List<MethodParameter> args = new ArrayList<>();
        Map<String, Integer> indexes = new HashMap<>();

        Parameter[] parameters = m.getParameters();
        int idx = 0;
        for (Parameter p : parameters) {
            args.add(new MethodParameter(p.getName(), p.getType()));
            indexes.put(p.getName(), idx);
            ++idx;
        }

        return new ParameterList(args, indexes, false, m.isVarArgs());
    }
}
