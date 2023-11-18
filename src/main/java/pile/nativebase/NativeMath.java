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
package pile.nativebase;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NativeMath {

	private static final BigInteger BIG_LONG_MAX = new BigInteger(Long.toString(Long.MAX_VALUE));
	private static final BigInteger BIG_INT_MAX = new BigInteger(Integer.toString(Integer.MAX_VALUE));

	
	public static byte to_byte(Number n) {
	    return n.byteValue();
	}
	
	@Precedence(0)
	@RenamedMethod("to-int")
	public static int toInt(BigInteger n) {
		if (n.compareTo(BIG_INT_MAX) > 0) {
			throw new TruncationException();
		}
		return n.intValue();
	}
	
	@Precedence(1)
	@RenamedMethod("to-int")
	public static int toInt(Number n) {
		// FIXME truncation
		return n.intValue();
	}
	
	@Precedence(1)
	@RenamedMethod("to-short")
	public static int toShort(Number n) {
		// FIXME truncation
		return n.shortValue();
	}
	
	@Precedence(0)
	@RenamedMethod("to-long")
	public static long toLong(BigInteger n) {
		if (n.compareTo(BIG_LONG_MAX) > 0) {
			throw new TruncationException();
		}
		return n.longValue();
	}
	
	@Precedence(1)
	@RenamedMethod("to-long")
	public static long toLong(Number n) {
		// FIXME truncation
		return n.longValue();
	}
	
	public static BigInteger to_bigint(Number n) {
	    if (n instanceof BigInteger bi) {
	        return bi;
	    } else {
	        return BigInteger.valueOf(n.longValue());
	    }
	}
	
	public static BigDecimal to_bigdec(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd;
        } else if (n instanceof BigInteger bi) {
            return new BigDecimal(bi);
        } else if (n instanceof Long l) {
            return BigDecimal.valueOf(l);
        } else {
            return BigDecimal.valueOf(n.doubleValue());
        }
    }
	
	public static int mod(int left, int right) {
	    return Math.floorMod(left, right);
	}
}
