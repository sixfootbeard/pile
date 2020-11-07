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

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.core.binding.NativeDynamicBinding.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import pile.collection.PersistentMap;
import pile.compiler.form.Nil;
import pile.compiler.typed.FunctionalInterfaceAdapter;
import pile.core.RuntimeRoot.ProtocolRecord;
import pile.core.exception.PileCompileException;
import pile.core.exception.ProtocolException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.guard.GuardBuilder;
import pile.core.indy.guard.ProtocolGuardBuilder;
import pile.core.indy.guard.ReceiverTypeGuard;
import pile.core.method.AbstractRelinkingCallSite;
import pile.core.method.HiddenCompiledMethod;
import pile.util.Pair;

public class ProtocolMethod implements PileMethod {

    private final Class<?> protocolClass;
    private final String methodName;
    private final Namespace ns;
    private final PileMethod method;

    public ProtocolMethod(Class<?> protoClass, String methodName, PileMethod hcm) {
        this.protocolClass = protoClass;
        this.methodName = methodName;
        this.ns = NAMESPACE.getValue();
        this.method = hcm;
    }

    @Override
    public boolean acceptsArity(int arity) {
        return method.acceptsArity(arity);
    }

    // TODO Make use return type

    @Override
    public Object invoke(Object... args) throws Throwable {
        ProtocolRecord protocolRecord = RuntimeRoot.getProtocolMetadata(protocolClass);
        PileMethod method = getTargetMethod(getClassN(args[0]), protocolRecord);
        return method.invoke(args);
    }
    
    private Class<?> getClassN(Object o) {
        return o == null ? null : o.getClass();
    }

    private PileMethod getTargetMethod(Class<? extends Object> base, ProtocolRecord protocolRecord) {
        Optional<PileMethod> opt;
        if (base != null && protocolClass.isAssignableFrom(base)) {
            opt = Optional.of(method);
        } else {
            opt = Optional.ofNullable(RuntimeRoot.lookupExtensionClass(protocolRecord, base, methodName));
        }
        return opt.orElseThrow(() -> new ProtocolException(
                "Unmatched protocol class for " + protocolClass.getSimpleName() + ": " + base));

    }

    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        // Might be tempting to specialize for known static types if it matches the
        // actual protocol interface but keep in mind that we still need to honor nil
        // being a possible base:
        // (defprotocol SomeProto ...)
        // (extend-protocol SomeProto
        //  nil
        //  ...) 
        // (defn [^SomeProto recv] (callfn recv))
        // Ambiguous call site target: 
        // invokeinterface (SomeProto) vs. invokevirtual (on nil's impl class)
        
        return Optional.empty();
    }

    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType staticTypes, long anyMask,
            CompilerFlags flags) {
        if (csType == CallSiteType.PLAIN) {
            return new AbstractRelinkingCallSite(staticTypes) {
                
                @Override
                protected MethodHandle findHandle(Object[] args) throws Throwable {
                    final MethodHandle staticTypedRelink = relink.asType(staticTypes);
                    
                    ProtocolRecord protocolRecord = RuntimeRoot.getProtocolMetadata(protocolClass);
                    Class<?> maybeClass = getClassN(args[0]);
                    PileMethod method = getTargetMethod(maybeClass, protocolRecord);
                    MethodHandle dynamicLink = method.dynamicLink(csType, staticTypes, anyMask, flags)
                            .dynamicInvoker();
                    
                    ProtocolGuardBuilder builder = new ProtocolGuardBuilder(dynamicLink, relink, staticTypes);
                    if (args[0] == null) {
                        builder.guardNull(0);
                    } else {
                        // Can't optimize this normally. If B is a subtype of A, and A is the proto impl
                        // class, we still can't do an instanceof A because there could be a class C
                        // which is a subtype of B which is its own proto impl class. 
                        
                        // OPTIMIZE if static type is final and no preferences can we elide this check?

                        if (! maybeClass.isPrimitive()) {
                            builder.guardExact(0, args[0].getClass());
                            builder.guardNotNull(0);
                        }
                    }
                    
                    var guard = builder.getHandle();
                    MethodHandle spGuarded = protocolRecord.switchPoint().guardWithTest(guard, staticTypedRelink);
                    
                    return spGuarded;
                }
            };
        } else if (csType == CallSiteType.PILE_VARARGS) {
            return PileMethod.super.dynamicLink(csType, staticTypes, anyMask, flags);
        } else {
            throw new PileCompileException("Unknown callsite type:" + csType);
        }

    }
    
    public static ProtocolMethod fromSingleAbstractMethodType(Lookup lookup, String methodName, Class<?> clazz) throws IllegalAccessException {
        Method method = FunctionalInterfaceAdapter.findMethodToImplement(clazz);
        int args = method.getParameterCount();
        MethodHandle handle = lookup.unreflect(method);
        
        MethodType type = handle.type();
        Class<?> returnType = type.returnType();
        
        PileMethod syn = new PileMethod() {
        
            @Override
            public Optional<Class<?>> getReturnType() {
                return Optional.of(returnType);
            }
            
            @Override
            public Object invoke(Object... args) throws Throwable {
                return handle.invoke(args);
            }
            
            @Override
            public boolean acceptsArity(int arity) {
                return type.parameterCount() == arity;
            }
        };
        
        ProtocolMethod result = new ProtocolMethod(clazz, methodName, syn);
        
        return result;
    }

}
