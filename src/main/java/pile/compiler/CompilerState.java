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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.util.CollectionUtils.*;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.ParameterParser.MethodParameter;
import pile.compiler.ParameterParser.ParameterList;
import pile.core.Symbol;
import pile.core.binding.NativeDynamicBinding;


/**
 * <ol>
 * <li> (enterClass|enterInterface)
 * <li> (enterMethod)...(leaveMethod)
 * <li> (leaveClass|leaveInterface)
 * @author john
 *
 */
public class CompilerState {
	
	private record ClassDataRecord(ClassVisitor classVisitor, ClassWriter classWriter, 
			Map<String, ClosureRecord> closureSymbols, String currentInternalName) {
	}
	
			
	private record SlackVariable(Class<?> type, int slot) {}
	
    private record MethodDataRecord(MethodVisitor methodVisitor, GeneratorAdapter generatorAdapter,
            Deque<ExceptionBlockTarget> tryTargets, Deque<LoopCompileTarget> loopTargets,
            AtomicReference<PersistentList<RecurId>> recurIds, Set<Integer> availableSlackVariables,
            List<SlackVariable> allSlackVariables, AtomicBoolean compileRecurPosition) {
        MethodDataRecord(MethodVisitor methodVisitor, GeneratorAdapter generatorAdapter) {
            this(methodVisitor, generatorAdapter, new ArrayDeque<>(), new ArrayDeque<>(),
                    new AtomicReference<>(PersistentList.EMPTY), new HashSet<>(), new ArrayList<>(),
                    new AtomicBoolean(true));
        }
    }

    public record AnnotationData(Class<? extends Annotation> clazz, Map<String, Object> args) {}

	public record ClosureRecord(String symbolName, String memberName, Class<?> type) {}
	
	
	// State
    private final MethodStack methodStack = new MethodStack();
	private final Scopes scope = new Scopes();
	
	private final Deque<LoopEvaluationTarget> letRecords = new ArrayDeque<>();
	
	// Per class
	private final Deque<ClassDataRecord> classDataRecords = new ArrayDeque<>();

	// Per method
	private final Deque<MethodDataRecord> methodDataRecords = new ArrayDeque<>();
	
	// Per evaluation of a macro
	private final AtomicLong autoSym = new AtomicLong();
	private final AtomicLong genSymReentrantCount = new AtomicLong();
	private String genSymPrefix = null;
		
	private AtomicBoolean evalRecurPosition = new AtomicBoolean(true);

	public CompilerState() {
	}

	public Scopes getScope() {
		return scope;
	}
	
	public MethodStack getMethodStack() {
		return methodStack;
	}

	public ClassVisitor getCurrentVisitor() {
		return lastCdr().classVisitor();
	}

	public byte[] compileClass() {
		return lastCdr().classWriter().toByteArray();
	}

	public MethodVisitor getCurrentMethodVisitor() {
		return lastMdr().methodVisitor();
	}
	
	public GeneratorAdapter getCurrentGeneratorAdapter() {
		return lastMdr().generatorAdapter();
	}

	public void enterClass(String internalName) {
		enterClass(internalName, Collections.emptyList());
	}
	
	public void enterClass(String internalName, List<Class<?>> interfaces) {
		enterClass(internalName, Object.class, interfaces);
	}

    public void enterClass(String internalName, Class<?> parent, List<Class<?>> interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        
        String filename = NativeDynamicBinding.COMPILE_FILENAME.deref();
        if (filename != null) {
            // TODO Eventually read JSR-045 for this second param
//            System.out.println(filename + " // " + internalName);
            writer.visitSource(filename, null);
        }

        // No stacks, currently
        ClassVisitor visitor = writer;

        visitor.visit(Opcodes.V15, ACC_PUBLIC, internalName, null, Type.getType(parent).getInternalName(),
                interfaces.stream().map(c -> getInternalName(c)).toArray(String[]::new));

        classDataRecords.add(new ClassDataRecord(visitor, writer, new LinkedHashMap<>(), internalName));
    }
	
	public void enterInterface(String internalName) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

		// No stacks, currently
		ClassVisitor visitor = writer;

		visitor.visit(Opcodes.V15, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, internalName, null,
				Type.getType(Object.class).getInternalName(), null);
		
		classDataRecords.add(new ClassDataRecord(visitor, writer, new LinkedHashMap<>(), internalName));
	}
	
	public void leaveInterface() {
		classDataRecords.removeLast();
	}

	public void leaveClass() {
		classDataRecords.removeLast();
	}

	// old
	public MethodVisitor enterMethod(String methodName, String methodDescriptor) {
		MethodVisitor method = lastCdr().classWriter().visitMethod(ACC_PUBLIC, methodName, methodDescriptor, null, null);
		GeneratorAdapter gen = new GeneratorAdapter(method, ACC_PUBLIC, methodName, methodDescriptor);
		
        MethodDataRecord mdr = new MethodDataRecord(method, gen);
		methodDataRecords.addLast(mdr);
		
		methodStack.enterMethod();
		
		return method;
	}
	
	public MethodVisitor enterMethod(String methodName, Class<?> returnType, int flags, ParameterList pr) {
		return enterMethod(methodName, returnType, flags, pr, Collections.emptyList());
	}
	
	public MethodVisitor enterMethod(String methodName, Class<?> returnType, int flags, ParameterList pr,
    		List<AnnotationData> annos) {
    	Type[] types = mapA(pr.args(), MethodParameter::getCompilableType, Type[]::new);
    	String descriptor = getMethodDescriptor(getType(toCompilableType(returnType)), types);
    	
    	ClassWriter classWriter = lastCdr().classWriter();
    	MethodVisitor method = classWriter.visitMethod(flags, methodName, descriptor, null, null);
    	
    	GeneratorAdapter gen = new GeneratorAdapter(method, flags, methodName, descriptor);
        MethodDataRecord mdr = new MethodDataRecord(method, gen);
    	methodDataRecords.addLast(mdr);
    	
    	methodStack.enterMethod();
    	
    	for (MethodParameter argRecord : pr.args()) {
    		method.visitParameter(argRecord.name(), 0);
    	}
    	for (var anno : annos) {
    		var visitor = method.visitAnnotation(getDescriptor(anno.clazz()), true);
    		anno.args().forEach((k, v) -> visitor.visit(k, v));
    		visitor.visitEnd();
    	}
    	
    	return method;
    }
    
    public int newSlackVariable(Class<?> type) {
        MethodDataRecord mdr = lastMdr();
        List<SlackVariable> allSlack = mdr.allSlackVariables();
        for (SlackVariable sv : allSlack) {
            Set<Integer> available = mdr.availableSlackVariables();
            if (sv.type().equals(type) && available.contains(sv.slot())) {
                available.remove(sv.slot());
                return sv.slot();
            }
        }
        int newLocal = getCurrentGeneratorAdapter().newLocal(getType(type));
        allSlack.add(new SlackVariable(type, newLocal));
        return newLocal;
    }
    
    public void returnSlackVariable(int slot) {
        lastMdr().availableSlackVariables().add(slot);
    }

    public void addClosureSymbol(String symbolName, String fieldName, Class<?> type) {
		lastCdr().closureSymbols().put(symbolName, new ClosureRecord(symbolName, fieldName, type));
	}
	
	public Map<String, ClosureRecord> getClosureSymbols() {
		return lastCdr().closureSymbols();
	}
	
	public void pushTryStart(ExceptionBlockTarget lt) {
		lastMdr().tryTargets().addLast(lt);
	}
	
	public void popTryStart() {
		lastMdr().tryTargets().removeLast();
	}
	
	public void pushLoopTarget(LoopCompileTarget lt) {
		lastMdr().loopTargets().addLast(lt);
	}
	
	public void popLoopTarget() {
		lastMdr().loopTargets().removeLast();
	}
	
	public LoopCompileTarget lastLoopTarget() {
		return lastMdr().loopTargets().getLast();
	}
	
	public void leaveMethod() {
		methodDataRecords.removeLast();		
		methodStack.leaveMethod();
	}
	
	public String getCurrentInternalName() {
		return lastCdr().currentInternalName();
	}

	public void pushLoopEvalTarget(LoopEvaluationTarget loopEvaluationTarget) {
		letRecords.addLast(loopEvaluationTarget);
	}
	
	public void popLoopEvalTarget() {
		letRecords.removeLast();
	}

	public LoopEvaluationTarget lastLoopEvalTarget() {
		return letRecords.getLast();
	}

	public String getClassName() {
		return lastCdr().currentInternalName();
	}

	public void pushAutoGensymScope() {
        if (genSymReentrantCount.getAndIncrement() == 0) {
            genSymPrefix = String.format("__%d__auto__", autoSym.getAndIncrement());
        }
    }

    public void popAutoGensymScope() {
        if (genSymReentrantCount.decrementAndGet() == 0) {
            genSymPrefix = null;        
        }
    }
    
    public String getGenSymSuffix() {
        requireNonNull(genSymPrefix, "Auto gensym suffix null");
        return genSymPrefix;
    }
    
    public boolean isCompiledRecurPosition() {
        return lastMdr().compileRecurPosition().get();
    }
    
    public void setCompileRecurPosition(boolean tail) {
        lastMdr().compileRecurPosition().set(tail);
    }
    
    public boolean isEvalRecurPosition() {
        return evalRecurPosition.get();
    }
    
    public void setEvalRecurPosition(boolean tail) {
        evalRecurPosition.set(tail);
    }
    
    private ClassDataRecord lastCdr() {
    	return classDataRecords.getLast();
    }

    private MethodDataRecord lastMdr() {
    	return methodDataRecords.getLast();
    }
    
    
    public record SavedStackSlot(int slot, TypeRecord typeRecord) {}
    
    public record SavedStack(List<SavedStackSlot> slots) {}

    /**
     * Generates instructions to restore the provided stack frame from locals.
     * Restores {@link MethodStack} to its previous state.
     * 
     * @param savedStack
     */
    public void restoreStack(SavedStack savedStack) {
        MethodStack stack = getMethodStack();
        GeneratorAdapter ga = getCurrentGeneratorAdapter();
        for (SavedStackSlot ss : savedStack.slots()) {
            ga.loadLocal(ss.slot());
            TypeRecord typeRecord = ss.typeRecord();
            stack.push(typeRecord.clazz(), ss.typeRecord().constant());
            returnSlackVariable(ss.slot());
        }
    }

    /**
     * Generates instructions to save the current stack to locals. The current
     * {@link MethodStack} will be empty upon return from this function.
     * 
     * @return A record which can be used to {@link #restoreStack(SavedStack)
     *         restore} the stack at some later point.
     */
    public SavedStack saveExistingStack() {
        MethodStack stack = getMethodStack();
        if (! stack.isEmpty()) {
            List<SavedStackSlot> out = new ArrayList<>();
            while (! stack.isEmpty()) {
                TypeRecord popR = stack.popR();
                int slot = newSlackVariable(popR.javaClass());
                // TODO save constants?
                SavedStackSlot saved = new SavedStackSlot(slot, popR);
                out.add(saved);
                
                GeneratorAdapter ga = getCurrentGeneratorAdapter();
                ga.storeLocal(slot);
            }
            Collections.reverse(out);
            return new SavedStack(out);
        } else {
            return new SavedStack(List.of());
        }
        
    }

    public static void boxTop(CompilerState cs) {
        MethodStack stack = cs.getMethodStack();
        Class<?> top = stack.pop();
        cs.getCurrentGeneratorAdapter().box(getType(top));
        stack.push(toWrapper(top));
    }
    
    

}
