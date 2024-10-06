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
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.CompilerState.AnnotationData;
import pile.compiler.MethodStack.InfiniteRecord;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.compiler.form.VarScope;
import pile.core.Symbol;

/**
 * Builder to compile a single method (via syntax or programmatically) to the
 * {@link CompilerState#getCurrentVisitor() current open class} being compiled.
 *
 */
public class SingleMethodCompilerBuilder {

    private static final Symbol DO_SYM = new Symbol("pile.core", "do");

    private final CompilerState cs;

    private Optional<Class<?>> returnType = Optional.empty();
    private List<AnnotationData> annos = new ArrayList<>();
    private int methodFlags = Opcodes.ACC_PUBLIC;
    private boolean hasThisArg = false;

    private String methodName;
    private ParameterList parseRecord;

    // body, either or
    Consumer<MethodVisitor> cons;
    PersistentList body;


    public SingleMethodCompilerBuilder(CompilerState cs) {
        this.cs = cs;
    }

    public SingleMethodCompilerBuilder withMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    public SingleMethodCompilerBuilder withParseRecord(ParameterList parseRecord) {
        this.parseRecord = parseRecord;
        return this;
    }
    
    /**
     * Whether the first {@link #withParseRecord(ParameterList) argument} represents
     * the 'this' field. Typically this is just the distinction between
     * member/static methods but there are also cases where the 'this' argument
     * exists but is hidden.
     * 
     * @param hasThisArg
     * @return
     */
    public SingleMethodCompilerBuilder withThisArgument(boolean hasThisArg) {
        this.hasThisArg = hasThisArg;
        return this;
    }

    public <A extends Annotation> SingleMethodCompilerBuilder withAnnotation(Class<A> clazz, Object... data) {
        Map<String, Object> args = mapOf(data);
        annos.add(new AnnotationData((Class<Annotation>) clazz, args));
        return this;
    }

    public SingleMethodCompilerBuilder withAnnotations(List<Class<Annotation>> more) {
        more.forEach(cl -> withAnnotation(cl));
        return this;
    }

    public SingleMethodCompilerBuilder withReturnType(Class<?> returnType) {
        this.returnType = Optional.of(returnType);
        return this;
    }

    public SingleMethodCompilerBuilder withBody(Consumer<MethodVisitor> cons) {
        this.cons = cons;
        return this;
    }

    public SingleMethodCompilerBuilder withBody(PersistentList body) {
        this.body = body;
        return this;
    }

    public SingleMethodCompilerBuilder withMethodFlags(int methodFlags) {
        this.methodFlags = methodFlags;
        return this;
    }

    public void build() {
        var scope = cs.getScope();
        scope.enterScope(VarScope.METHOD);
        
        List<ClassSlot> methodParams = new ArrayList<>();

        // Smuggling in this arg because genadapter uses 0 indexed arg lookup even
        // though aload_0 is 'this', ga.loadArg(0) is the first parameter. This is fixed
        // during lookup in SymbolForm#compileSLR
        int index = hasThisArg ? -1 : 0;
        for (MethodParameter ar : parseRecord.args()) {
            methodParams.add(new ClassSlot(toCompilableType(ar.type()), index));
            
            scope.addCurrent(ar.name(), ar.type(), index, null);
            ++index;
        }
        
        var actualReturnType = returnType.map(Helpers::toCompilableType).orElse(Object.class);      

        var prMod = hasThisArg ? parseRecord.popFirst() : parseRecord;
        MethodVisitor method = cs.enterMethod(methodName, actualReturnType, methodFlags, prMod, annos);
        try {
            if (cons != null) {
                cons.accept(method);
            } else {
                requireNonNull(body, "Body must be set");
                method.visitCode();

                // Loop target
                Label methodStart = new Label();
                LoopCompileTarget lt = new LoopCompileTarget(methodStart, prMod.args().size(),
                        LoopTargetType.METHOD_ARGS, methodParams, new ArrayList<>());
                cs.pushLoopTarget(lt);
                method.visitLabel(methodStart);

                // Compile form
                Compiler.compile(cs, body.conj(DO_SYM));
                MethodStack methodStack = cs.getMethodStack();
                switch (methodStack.popR()) {
                    case TypeRecord tr -> {
                        Class<?> targetType = tr.javaClass();
                        createReturnInsn(cs, actualReturnType, targetType);
                    }
                    case InfiniteRecord _ -> {
                        // pass
                        // entire function was an infinite loop which is fine.
                    }
                };
            }
            method.visitMaxs(0, 0);
            method.visitEnd();
        } catch (Throwable t) {
//            t.printStackTrace();
            throw t;
        } finally {
            cs.leaveMethod();
            cs.getScope().leaveScope();
        }
    }

    /**
     * Generate a return insn that satisfies the return type and stack state.
     * 
     * @param cs
     * @param returnType The expected return type of the function.
     * @param topType    The type of the value on the top of the stack.
     */
    private void createReturnInsn(CompilerState cs, Class<?> returnType, Class<?> topType) {
        GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
        
        topType = toCompilableType(topType);
        returnType = toCompilableType(returnType);

        if (Void.TYPE.equals(returnType)) {
            ga.visitInsn(Opcodes.RETURN);
        } else {
            // topType, expectedType
            // ref, ref -> ARETURN
            // ref, prim, -> unbox, XRETURN
            // prim, ref, -> box, ARETURN
            // prim, prim, -> x2x, XRETURN
            
            if (returnType.isPrimitive()) {
                if (!Void.TYPE.equals(returnType)) {
                    if (!topType.isPrimitive()) {
                        Type wrappedReturnType = getType(toWrapper(returnType));
                        cs.getCurrentMethodVisitor().visitTypeInsn(Opcodes.CHECKCAST, wrappedReturnType.getInternalName());
                        ga.unbox(getType(returnType));
                        topType = returnType;// (topType);
                    } else {
                        if (!(topType.equals(returnType))) {
                            // FIXME Maybe don't do narrowing conversions implicitly.
                            ga.cast(getType(topType), getType(returnType));
                        }
                    }
                }
            } else {
                // && !returnType.isPrimitive()
                if (topType.isPrimitive()) {
                    ga.box(getType(topType));
                    topType = toWrapper(topType);
                }
                if (!(topType.equals(returnType))) {
                    if (!returnType.isAssignableFrom(topType)) {
                        cs.getCurrentMethodVisitor().visitTypeInsn(Opcodes.CHECKCAST,
                                getType(returnType).getInternalName());
                    }
                }
            }
            ga.returnValue();
        }
    }

    private Map<String, Object> mapOf(Object[] data) {
        ensure(data.length % 2 == 0, "Wrong sized annotation data map");
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < data.length; i += 2) {
            var key = data[i];
            if (key instanceof String skey) {
                map.put(skey, data[i + 1]);
            } else {
                throw error("Map key must be a string");
            }
        }
        return map;
    }

}
