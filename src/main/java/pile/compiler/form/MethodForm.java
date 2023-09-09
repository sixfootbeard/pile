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
import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static pile.compiler.Constants.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.compiler.AbstractClassCompiler.CompiledMethodResult;
import pile.compiler.ClosureClassCompiler;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.CompilerState.ClosureRecord;
import pile.compiler.DeferredCompilation;
import pile.compiler.Helpers;
import pile.compiler.MethodCollector.MethodArity;
import pile.compiler.MethodDefiner;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.typed.Any;
import pile.core.CoreConstants;
import pile.core.Namespace;
import pile.core.PileMethod;
import pile.core.Symbol;
import pile.core.binding.NativeDynamicBinding;
import pile.core.compiler.aot.AOTHandler;
import pile.core.compiler.aot.AOTHandler.AOTType;
import pile.core.exception.PileCompileException;
import pile.core.exception.PileInternalException;
import pile.core.log.Logger;
import pile.core.log.LoggerSupplier;
import pile.core.method.ClosureCompiledMethod;
import pile.core.method.HiddenCompiledMethod;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.ParserConstants;
import pile.core.parse.TypeTag;
import pile.util.InvokeDynamicBootstrap;

public class MethodForm implements Form {

    private static final Logger LOG = LoggerSupplier.getLogger(MethodForm.class);
    
    private static final Lookup MC_LOOKUP = lookup();
	private static final Type HCM_TYPE = getType(PileMethod.class);

    private final Namespace ns;
    private final PersistentList form;
    private final String targetPackage;

    private Class<?> clazz;

    public MethodForm(PersistentList form) {
        this.form = form;
        this.ns = NativeDynamicBinding.NAMESPACE.getValue();
        this.targetPackage = CoreConstants.GEN_PACKAGE /* + "/" + className */;
    }
    
	@Override
	public HiddenCompiledMethod evaluateForm(CompilerState cs) throws Throwable {
		CompiledMethodResult cmr = compileClass(cs);
		Class<?> compileClass = cmr.clazz(); 
		
		List<Object> args = new ArrayList<>();

        for (var clos : cmr.closureSymbols().entrySet()) {
            // FIXME Should use actual symbol instead of synthetic one
            args.add(Compiler.evaluate(cs, new Symbol(clos.getValue().symbolName())));
        }

		return collectAndCreateInstance(compileClass, args);
	}

    public static HiddenCompiledMethod collectAndCreateInstance(Class<?> compileClass, List<Object> args)
            throws InvocationTargetException {
        Object instance;
        MethodArity m;
        try {
            m = MethodDefiner.collectPileMethods(compileClass);
            // TODO Should only be one, right?
            Constructor<?> cons = compileClass.getDeclaredConstructors()[0];
            instance = cons.newInstance(args.toArray(Object[]::new));
        } catch (IllegalAccessException | InstantiationException e) {
            throw new PileInternalException("Error accessing generated method constructor in:" + compileClass, e);
        }
        return toCompiledMethod(m, instance);
    }
	
	@Override
	public DeferredCompilation compileForm(CompilerState comp) {
		CompiledMethodResult cmr = compileClass(comp);	
		return compileCallClosure(cmr);	
	}

    public CompiledMethodResult compileClass(CompilerState cs) {
		// Preamble

		// chuck 'fn'
		PersistentList seq = form.pop();
		Object second = first(seq);
		TypeTag type = type(second);
		
		String className;
		if (type == TypeTag.SYMBOL) {
		    className = second.toString() + "$" + ns.getSuffix();
		    seq = form.pop().pop();
		    second = first(seq);
	        type = type(second);
		} else {
		    className = "fclass$anon$" + ns.getSuffix();
		}
		// AOT
		if (AOTHandler.getAotType() == AOTType.WRITE) {
		    className = "aot$"+ className;
		}
		
		var compiler = new ClosureClassCompiler(ns, className, targetPackage);

        try (var ignored = compiler.enterClass(cs)) {
            Class<?> anno = Helpers.getTypeHint(form, ns).orElse(Any.class);
            switch (type) {
                case VEC:
                    // (fn [a b c] ...)
                    compiler.createSingleMethod(cs, anno, seq);
                    break;
                case SEXP:
                    // (fn ([a] a) ([a b] b))
                    for (Object arity : seq) {
                        Class<?> innerAnno = (Class<?>) ParserConstants.ANNO_TYPE_KEY.call(meta(form));
                        // TODO WARN if different
                        compiler.createSingleMethod(cs, innerAnno == null ? anno : innerAnno, expectList(arity));
                    }
                    break;
                default:
                    throw new PileCompileException("Unexpected form type: " + type, LexicalEnvironment.extract(second, form));
            }

            compiler.defineConstructor(cs);
            compiler.exitClass(cs);
            return compiler.wrap(cs);
        } catch (IllegalAccessException e) {
            throw new PileInternalException(e);
        }
       
	}

	public Class<?> getClazz() {
    	if (clazz == null) {
    		throw new PileInternalException("Called in the wrong order");
    	}
    	return clazz;
    }

	public static DeferredCompilation compileCallClosure(CompiledMethodResult cmr) {
        Class<?> clazz = cmr.clazz();
    	Map<String, ClosureRecord> symbols = cmr.closureSymbols();
    	
    	return new DeferredCompilation(TypeTag.SEXP, clazz, (cs) -> {
    		MethodVisitor mv = cs.getCurrentMethodVisitor();
    
    		Handle h = new Handle(H_INVOKESTATIC, Type.getType(MethodForm.class).getInternalName(), "bootstrap",
    		getBootstrapDescriptor(getType(Class.class)), false);
    
    		if (symbols.isEmpty()) {
    			mv.visitInvokeDynamicInsn("instantiateClass", Type.getMethodDescriptor(HCM_TYPE), h,
    					toConst(clazz).get());
    		} else {
    			// Closures
    			int count = 0;
    			for (String sym : symbols.keySet()) {
    				Compiler.compile(cs, new Symbol(sym));
    				++count;
    			}
    			List<TypeRecord> popN = cs.getMethodStack().popN(count);
    			mv.visitInvokeDynamicInsn("instantiateClass", getMethodDescriptor(HCM_TYPE, getJavaTypeArray(popN)), h,
    					toConst(clazz).get());
    		}
    		
    		cs.getMethodStack().push(PileMethod.class);
    	});
    }

    private static HiddenCompiledMethod toCompiledMethod(MethodArity m, Object instance) {
        HiddenCompiledMethod compiled = new HiddenCompiledMethod(instance.getClass(), m.bind(instance));
		return compiled;
	}

	@InvokeDynamicBootstrap
	public static CallSite bootstrap(Lookup caller, String method, MethodType type, Class<?> clazz) throws Throwable {
		MethodArity m = MethodDefiner.collectPileMethods(clazz);
		
		// may be either noargs or the closure constructor
		Constructor<?>[] constructors = clazz.getConstructors();
		MethodHandle cons = caller.unreflectConstructor(constructors[0]);
		
		if (type.parameterCount() == 0) {
			// plain class
			Object instance = cons.invoke();
			HiddenCompiledMethod compiled = toCompiledMethod(m, instance);			
			return new ConstantCallSite(constant(HiddenCompiledMethod.class, compiled).asType(type));
		} else {
			// closure
			// in order:
			// c = constructor(closure-arg-0, closure-arg-1, closure-arg-2, .. closure-arg-N)
			// clos = ClosureCompiledMethod(c, methodArity^)
			
            MethodType consType = methodType(void.class, Class.class, Object.class, MethodArity.class);
            MethodHandle closureCons = MC_LOOKUP.findConstructor(ClosureCompiledMethod.class, consType);
            MethodHandle boundClass = insertArguments(closureCons, 0, clazz);
            MethodHandle boundArity = insertArguments(boundClass, 1, m);
            MethodHandle typedBound = boundArity.asType(boundArity.type().changeParameterType(0, cons.type().returnType()));
            MethodHandle out = collectArguments(typedBound, 0, cons);
            return new ConstantCallSite(out.asType(type));
		}
	}
}
