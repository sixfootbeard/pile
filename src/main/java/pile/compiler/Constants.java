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
import static org.objectweb.asm.Type.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;

import java.lang.invoke.MethodHandles.Lookup;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Type;

import pile.compiler.form.BooleanForm;
import pile.compiler.form.NumberForm;
import pile.core.ConstForm;
import pile.core.indy.IndyHelpers;
import pile.util.ConstantDynamicBootstrap;

public class Constants {

	// TODO Complete these
	private static Set<Class<?>> SIMPLE_CONSTANT_CLASSES = Set.of(Integer.class, String.class, Long.class, Double.class,
			Short.class, Byte.class);
			
	/**
	 * 
	 * @param deref A form to find a constant
	 * @return A constant form of the object, or empty. 
	 */
	@SuppressWarnings("preview")
    public static Optional<?> toConst(Object deref) {
		if (deref == null) {
			return Optional.empty();
        }
        var oclazz = deref.getClass();
        if (SIMPLE_CONSTANT_CLASSES.contains(oclazz)) {
            return Optional.of(deref);
        }
        return switch (deref) {
            case ConstForm<?> con -> con.toConst();
            case Class<?> clz -> Optional.of(getType(clz));
            case Enum<?> enumVal -> Optional.of(IndyHelpers.forEnum(enumVal));
            case Boolean b -> Optional.of(BooleanForm.toCondy(b));
            case MethodType mt -> Optional.of(makeCondy("make", Constants.class, "methodType",
                    getBootstrapDescriptor(STRING_TYPE), MethodType.class, mt.descriptorString()));
            case BigInteger bi -> Optional.of(makeCondy("bigint", NumberForm.class, "bigint",
                    getConstantBootstrapDescriptor(getType(BigInteger.class), STRING_TYPE), BigInteger.class,
                    bi.toString()));
            case BigDecimal bd -> Optional.of(makeCondy("bigdecimal", NumberForm.class, "bigdecimal",
                    getConstantBootstrapDescriptor(getType(BigDecimal.class), STRING_TYPE), BigDecimal.class,
                    bd.toString()));
            default -> Optional.empty();
        };
	}
	
	public static Optional<?> toConstAndNull(Object v) {
	    if (v == null) {
            ConstantDynamic condy = nullCondy();
            return Optional.of(condy);
	    }
	    return toConst(v);
	}

    public static ConstantDynamic nullCondy() {
        ConstantDynamic condy = makeCondy("nullConst", ConstantBootstraps.class, "nullConstant",
                getConstantBootstrapDescriptor(Object.class), Object.class);
        return condy;
    }  
	
	
    
    @ConstantDynamicBootstrap
    public static MethodType methodType(Lookup lookup, String name, Class<MethodType> clazz, String desc) {
        return fromMethodDescriptorString(desc, Constants.class.getClassLoader());
    }

}
