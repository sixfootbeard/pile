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
package pile.core.method;

import static java.lang.invoke.MethodHandles.*;
import static pile.compiler.Helpers.*;
import static pile.core.method.CommonHandles.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.PileMethod;
import pile.core.exception.PileInternalException;
import pile.core.indy.CallSiteType;
import pile.core.indy.CompilerFlags;
import pile.core.indy.HandleType;
import pile.nativebase.method.PileInvocationException;

/**
 * Wrapper for method handles for compiled methods. Don't leak.
 *
 */
public abstract class AbstractCompiledMethod implements PileMethod {

    public static final Keyword FINAL_KEY = Keyword.of(null, "final");
    
    private final Class<?> backing;
    
    public AbstractCompiledMethod(Class<?> backing) {
        super();
        this.backing = backing;
    }

    @Override
    public boolean acceptsArity(int arity) {
        return (getArityHandles() != null && getArityHandles().containsKey(arity))
                || (getVarArgsArity() != -1 && getVarArgsArity() <= arity);
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        int argSize = args.length;
        MethodHandle methodHandle = getArityHandles().get(argSize);
        if (methodHandle == null) {
            if (getVarArgsMethod() != null && getVarArgsArity() <= args.length) {
                methodHandle = getVarArgsMethod();

                Object[] toCall = new Object[getVarArgsArity() + 1];
                System.arraycopy(args, 0, toCall, 0, getVarArgsArity());
                int copySize = args.length - getVarArgsArity();
                if (copySize > 0) {
                    Object[] end = Arrays.copyOfRange(args, getVarArgsArity(), getVarArgsArity() + copySize);
                    toCall[toCall.length - 1] = seq(end);
                } else {
                    toCall[toCall.length - 1] = ISeq.EMPTY;
                }
                args = toCall;
            }
        }
        if (methodHandle == null) {
            String sizes = getArityHandles().keySet().stream().map(i -> i.toString())
                    .collect(Collectors.joining(", ", "[", "]"));
            throw new IllegalArgumentException(
                    "Method called with the wrong number of arguments. Saw size=" + args.length + " expected=" + sizes);
        }
        return call(methodHandle, args);
    }
    
    public Class<?> getBacking() {
        return backing;
    }

    @Override
    public Object applyInvoke(Object... args) throws Throwable {
        // We're trying to optimize cases where an apply is used on a method with
        // varargs support so we don't need to unpack all the arguments.
        if (getVarArgsArity() == -1) {
            // Nothing to optimize, just use default impl
            return PileMethod.super.applyInvoke(args);
        }
        // (defn f [a b c & d] ...)
        // (apply f [a b c d]) pull 3 args out
        // (apply f a b c d e f []) consume 3 args

        // Un/wrap args towards varargs until we have an empty next or we're at the
        // varargs impl

        List<Object> argList = new ArrayList<>(Arrays.asList(args));
        
        var argLength = args.length;
        int plainArgSize = argLength - 1;
        if (plainArgSize == getVarArgsArity()) {
            // TODO Decide if we want to always call seq here
            ISeq last = (ISeq) seq(last(argList));
            pop(argList); // pop last
            argList.add(last);
            return call(getVarArgsMethod(), argList.toArray());
        }
        if (plainArgSize > getVarArgsArity()) {
            // TODO Decide if we want to always call seq here
            ISeq last = (ISeq) seq(last(argList));
            pop(argList); // pop iseq
            for (int i = plainArgSize; i > getVarArgsArity(); --i) {
                var localEnd = last(argList);
                last = cons(localEnd, last);
                pop(argList);
            }
            argList.add(last); // new varargs
            return call(getVarArgsMethod(), argList.toArray());
        } else /* plainArgSize < varArgsAirity */ {
            // Slightly different than above, we may need to bail early and call a
            // non-varargs method if the sequence ends.
            
            // (defn f ([a b] ...) ([a b & c] ..))
            // (apply f a [b]) Calls the 2 arg because the sequence ends early
            // (apply f a b [c]) Calls the 3 arg version

            // TODO Decide if we want to always call seq here
            ISeq last = (ISeq) seq(last(argList));
            pop(argList);
            while (last != null) {
                if (plainArgSize == getVarArgsArity()) {
                    argList.add(last);
                    return call(getVarArgsMethod(), argList.toArray());
                }

                argList.add(last.first());
                last = last.next();
                ++plainArgSize;
            }
            if (plainArgSize == getVarArgsArity()) {
                argList.add(null);
                return call(getVarArgsMethod(), argList.toArray());
            }
            // argList has a null terminator, check exact match
            // pop?
            MethodHandle exactMatch = getArityHandles().get(argList.size());
            if (exactMatch == null) {
                throw new IllegalArgumentException();
            }
            return call(exactMatch, argList.toArray());
        }
    }

    @Override
    public Optional<CallSite> staticLink(CallSiteType csType, MethodType staticTypes, long anyMask) {
        int paramCount = staticTypes.parameterCount();
        
        switch (csType) {
            case PLAIN: {
                HandleLookup handle = findNormalHandle(paramCount);
                var ccs = new ConstantCallSite(adaptNormalMethod(handle, paramCount).asType(staticTypes));
                return Optional.of(ccs);
            }
            case PILE_VARARGS: {
                if (getVarArgsArity() == -1) {
                    // exact arity match
                    // (defn ^:final f [a b c] ...)
                    // (apply f a [b c])
                    
                    int maxSize = Collections.max(getArityHandles().keySet());
                    MethodHandle[] handles = new MethodHandle[maxSize + 1];
                    
                    // Create handles that match the exact size of the args.
                    Map<Integer, MethodHandle> adapted = new HashMap<>();
                    for (var entry : getArityHandles().entrySet()) {
                        var h = preCall(entry.getValue());
                        int handleParamCount = h.type().parameterCount();
                        int delta = handleParamCount - paramCount + 1;

                        MethodHandle spreader = h.asSpreader(Object[].class, delta);
                        MethodHandle unrollExactBound = insertArguments(UNROLL_EXACT, 0, delta);
                        MethodHandle filtered = filterArguments(spreader, paramCount - 1, unrollExactBound);
                        MethodHandle lastSeq = filterArguments(filtered, filtered.type().parameterCount() - 1, TO_SEQ);
                        int arity = entry.getKey();
                        adapted.put(arity, lastSeq);
                        handles[arity] = lastSeq;
                    }
                    // Fill in rest with exceptions
                    for (int i = 0; i < handles.length; ++i) {
                        if (handles[i] == null) {
                            MethodHandle throwing = throwException(staticTypes.returnType(), PileInvocationException.class);
                            PileInvocationException ex = new PileInvocationException("Invalid arity size: " + i);
                            MethodHandle bound = insertArguments(throwing, 0, ex);
                            MethodHandle dropped = dropArguments(bound, 0, staticTypes.parameterList());
                            handles[i] = dropped;
                        }
                    }
 
                    
                    // (handle, staticTypes*)
                    MethodHandle invoker = invoker(staticTypes);
                    
                    // (MethodHandle[], int) -> MethodHandle
                    MethodHandle arrayGet = arrayElementGetter(MethodHandle[].class);
                    
                    // (int) -> MethodHandle
                    arrayGet = arrayGet.bindTo(handles);
                    
                    // (int, staticTypes*)
                    MethodHandle withInt = filterArguments(invoker, 0, arrayGet);
                    
                    // guard > array size to same exception
                    MethodHandle throwing = throwException(staticTypes.returnType(), PileInvocationException.class);
                    PileInvocationException ex = new PileInvocationException("Invalid arity size, too big");
                    MethodHandle bound = insertArguments(throwing, 0, ex);
                    MethodHandle dropped = dropArguments(bound, 0, withInt.type().parameterList());
                    
                    MethodHandle boundGt = insertArguments(LT, 1, handles.length);
                    MethodHandle highEndTest = guardWithTest(boundGt, withInt, dropped);
                    
                    // (ISeq, staticTypes*)
                    MethodHandle counted = filterArguments(highEndTest, 0, COUNT);
                    
                    // Permute iseq to front
                    // [1, 0, 1]
                    int[] argOrder = new int[staticTypes.parameterCount() + 1];
                    argOrder[0] = staticTypes.parameterCount() - 1;
                    for (int i = 0; i < staticTypes.parameterCount(); ++i) {
                        argOrder[i+1] = i;
                    }
                    
                    // (staticTypes*)
                    MethodType withPermutation = staticTypes.insertParameterTypes(0, staticTypes.lastParameterType());
                    MethodHandle result = permuteArguments(counted.asType(withPermutation), staticTypes, argOrder);
                    return Optional.of(new ConstantCallSite(result));
                    
                } else {
                    // []
                    // [a]
                    // [a b]
                    // [a b & c]
                    
                    // Considerations
                    // - Exact arity match always wins over a varargs [1 2] matches [a b] not [a b & c]
                    // - Some handles are impossible to match (apply f 1 2 tail) can't match [a]
                    // - Don't necessarily want to count because this could be applied to an
                    // infinite sequence, so we have unroll iteratively
                    
                    // May have to aggregate too:
                    // [a b c & d]
                    // Called with (apply f 1 [2 3 4])
                    
                    // RETHINK I'm not sure there's actually any good impls here unless we can count things                    
                    return Optional.empty();
                }
            }
            default:
                throw new PileInternalException("Unhandles callsite type:" + csType);            
        }
    }

    @Override
    public CallSite dynamicLink(CallSiteType csType, MethodType staticTypes, long anyMask, CompilerFlags flags) {
        var maybeCs = staticLink(csType, staticTypes, anyMask);
        if (maybeCs.isEmpty()) {
            MethodHandle linked = LinkableMethod.invokeLink(csType, this).asType(staticTypes);
            return new ConstantCallSite(linked);
        }   
        return maybeCs.get();                
    }

    private void pop(List<Object> argList) {
        argList.remove(argList.size() - 1);
    }

    private Object last(List<Object> args) {
        return args.get(args.size() - 1);
    }

    private boolean matchesVarArgs(int argSize) {
        return getVarArgsArity() != -1 && getVarArgsArity() >= argSize;
    }

    private MethodHandle adaptNormalMethod(HandleLookup toCall, int expectedSize) {
        MethodHandle toCallHandle = preCall(toCall.handle());
        if (toCall.htype() == HandleType.NORMAL) {
            return toCallHandle;
        } else if (toCall.htype() == HandleType.VARARGS) {
            MethodType handleType = toCallHandle.type();

            int handleSize = handleType.parameterCount();
            // (def f (fn [a & b] ... ))

            if (expectedSize + 1 == handleSize) {
                // (f a)

                // Handle type includes the trailing Iseq but our caller didn't use it, so we
                // have to insert an empty to match.
                return MethodHandles.insertArguments(toCallHandle, expectedSize, ISeq.EMPTY);
            } else {
                // (f a b ...)

                // (Object, ISeq)
                MethodHandle adaptedSeqFromArray = MethodHandles.filterArguments(toCallHandle, handleSize - 1,
                        SEQ_FROM_ARRAY);
                // (Object, Object[])
                return adaptedSeqFromArray.asCollector(handleSize - 1, Object[].class, expectedSize - handleSize + 1);
                // (Object, Object...)
            }

        }
        throw error("Unsupported handle type:" + toCall.htype());
    }
    
    @SuppressWarnings("unused")
    private static boolean lessThan(int l, int r) {
        return l < r;
    }

    private HandleLookup findNormalHandle(int paramCount) {
        Map<Integer, MethodHandle> methodTable = getArityHandles();
        MethodHandle methodHandle = methodTable.get(paramCount);
        if (methodHandle != null) {
            // Direct arity match
            return new HandleLookup(HandleType.NORMAL, paramCount, methodHandle);
        } else {
            MethodHandle varargs = getVarArgsMethod();
            if (varargs != null) {
                return new HandleLookup(HandleType.VARARGS, paramCount, varargs);
            } else {
                // TODO Unrolled kw args
                throw new IllegalArgumentException("Couldn't figure out how to link method");
            }
        }
    }
    
    protected MethodHandle preCall(MethodHandle h) {
        return h;
    }
    
    protected Object call(MethodHandle methodHandle, Object... args) throws Throwable {
        return preCall(methodHandle).invokeWithArguments(args);
    }

    protected abstract Integer getVarArgsArity();

    protected abstract MethodHandle getVarArgsMethod();

    protected abstract Map<Integer, MethodHandle> getArityHandles();

}
