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

import static org.objectweb.asm.Type.*;
import static pile.compiler.Constants.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import org.objectweb.asm.MethodVisitor;

import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.core.binding.NativeDynamicBinding;
import pile.core.parse.TypeTag;
import pile.util.ConstantDynamicBootstrap;

public class NumberForm implements Form {

	private final Number v;

	public NumberForm(Number n) {
		this.v = n;
	}

	@Override
	public DeferredCompilation compileForm(CompilerState compilerState) {
	    
	
        final Number localNum = getNumber();

        if (localNum instanceof BigInteger bi) {
            Optional condy = toConst(bi);
            return new DeferredCompilation(TypeTag.NUMBER, bi, condy, cs -> {
                cs.getCurrentMethodVisitor().visitLdcInsn(condy.get());
                cs.getMethodStack().pushConstant(localNum.getClass());
            });
        } else if (localNum instanceof BigDecimal bd) {
            Optional condy = toConst(bd);
            return new DeferredCompilation(TypeTag.NUMBER, bd, condy, cs -> {
                cs.getCurrentMethodVisitor().visitLdcInsn(condy.get());
                cs.getMethodStack().pushConstant(localNum.getClass());
            });
        } else {

            boolean boxedNumbers = NativeDynamicBinding.BOXED_NUMBERS.getValue();
            if (boxedNumbers) {
                return new DeferredCompilation(TypeTag.NUMBER, localNum, Optional.of(localNum), (cs) -> {
                    cs.getCurrentMethodVisitor().visitLdcInsn(localNum);
                    cs.getCurrentGeneratorAdapter().box(getType(wrapperToPrimitive(localNum.getClass())));
                    cs.getMethodStack().pushConstant(localNum.getClass());
                });
            } else {
                return new DeferredCompilation(TypeTag.NUMBER, localNum, Optional.of(localNum), (cs) -> {
                    MethodVisitor mv = cs.getCurrentMethodVisitor();
                    mv.visitLdcInsn(localNum);
                    cs.getMethodStack().pushConstant(wrapperToPrimitive(localNum.getClass()));
                });
            }
        }
	}

    private Number getNumber() {
        return v;
    }

	@Override
	public Number evaluateForm(CompilerState cs) throws Throwable {
		return getNumber();
	}
	
	
    @ConstantDynamicBootstrap
    public static BigInteger bigint(Lookup lookup, String name, Class<BigInteger> clazz, String repr) {
        return new BigInteger(repr);
    }
    
    @ConstantDynamicBootstrap
    public static BigDecimal bigdecimal(Lookup lookup, String name, Class<BigDecimal> clazz, String repr) {
        return new BigDecimal(repr);
    }

}
