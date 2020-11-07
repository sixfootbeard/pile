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
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.nativebase.NativeCore.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import pile.compiler.Helpers;
import pile.compiler.MethodCollector.MethodArity;
import pile.core.ISeq;
import pile.core.Keyword;
import pile.core.PCall;
import pile.core.PileMethod;
import pile.core.exception.PileException;
import pile.nativebase.NativeCore;
import pile.nativebase.method.PileInvocationException;

/**
 * Wrapper for method handles for compiled methods. Don't leak.
 *
 */
public class HiddenCompiledMethod extends AbstractCompiledMethod {

    public static final Keyword FINAL_KEY = Keyword.of(null, "final");

    // can't put kw/var in here otherwise they might be called with the wrong
    // calling conventions.
    private final Map<Integer, MethodHandle> arityHandles;
    private final MethodHandle varArgsMethod;
    private final Integer varArgsArity;

    @SuppressWarnings("unused") // Used by the linker
    private final MethodHandle kwMethodUnpacked;
    
    private final String methodName;

    public HiddenCompiledMethod(Class<?> backing, Map<Integer, MethodHandle> arityHandles, MethodHandle varArgsMethod,
            Integer varArgsArity, MethodHandle kwMethodUnpacked) {
        this(backing, "anon", arityHandles, varArgsMethod, varArgsArity, kwMethodUnpacked);
    }
    
    public HiddenCompiledMethod(Class<?> backing, String name, Map<Integer, MethodHandle> arityHandles, MethodHandle varArgsMethod,
            Integer varArgsArity, MethodHandle kwMethodUnpacked) {
        super(backing);
        this.methodName = name;
        this.arityHandles = arityHandles;
        this.varArgsMethod = varArgsMethod;
        this.varArgsArity = varArgsArity;
        this.kwMethodUnpacked = kwMethodUnpacked;
    }
    
    public HiddenCompiledMethod(Class<?> backing, MethodArity ma) {
        super(backing);    
        this.arityHandles = ma.arityHandles();
        this.varArgsMethod = ma.varArgsMethod();
        this.varArgsArity = ma.varArgsSize();
        this.kwMethodUnpacked = ma.kwArgsUnrolledMethod();
        this.methodName = null; // TODO
    }    

    public HiddenCompiledMethod withDefaults(HiddenCompiledMethod defaultMethods) {
        Map<Integer, MethodHandle> arityHandles = new HashMap<>(defaultMethods.arityHandles);
        arityHandles.putAll(this.arityHandles);

        Integer varArgsArity = this.varArgsArity;
        MethodHandle varArgsMethod = this.varArgsMethod;
        if (this.varArgsArity == -1) {
            varArgsArity = defaultMethods.varArgsArity;
            varArgsMethod = defaultMethods.varArgsMethod;
        } 
        MethodHandle kwMethodUnpacked = this.kwMethodUnpacked;
        if (kwMethodUnpacked == null) {
            kwMethodUnpacked = defaultMethods.kwMethodUnpacked;
        }
        return new HiddenCompiledMethod(getBacking(), arityHandles, varArgsMethod, varArgsArity, kwMethodUnpacked);        
    }

    @Override
    protected MethodHandle preCall(MethodHandle h) {
        return h;
    }
    
    @Override
    protected Map<Integer, MethodHandle> getArityHandles() {
        return arityHandles;
    }
    
    @Override
    protected Integer getVarArgsArity() {
        return varArgsArity;
    }
    
    @Override
    protected MethodHandle getVarArgsMethod() {
        return varArgsMethod;
    }

}
