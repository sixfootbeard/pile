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
package pile.compiler.math;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberMethods {

    //@formatter:off
	public static int plus(int lhs, int rhs) { return lhs + rhs; }
	public static long plus(long lhs, long rhs) { return lhs + rhs; }
	public static float plus(float lhs, float rhs) { return lhs + rhs; }
	public static double plus(double lhs, double rhs) { return lhs + rhs; }
	public static BigInteger plus(BigInteger lhs, BigInteger rhs) { return lhs.add(rhs); }
	public static BigDecimal plus(BigDecimal lhs, BigDecimal rhs) { return lhs.add(rhs); }
	
	public static Number plusOverflow(int lhs, int rhs) {
	    try {
            return Math.addExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return plusOverflow((long)lhs, (long)rhs);
        }
    }
    
	public static Number plusOverflow(long lhs, long rhs) {
        try {
            return Math.addExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return plusOverflow(BigInteger.valueOf(lhs), BigInteger.valueOf(rhs));
        }
    }
    
	public static Number plusOverflow(double lhs, double rhs) {
        double out = lhs + rhs;
        if (Double.isInfinite(out)) {
            return plusOverflow(BigDecimal.valueOf(lhs), BigDecimal.valueOf(rhs));
        } else {
            return out;
        }
    }
    
	public static Number plusOverflow(BigInteger lhs, BigInteger rhs) {
        return lhs.add(rhs);       
    }
	public static Number plusOverflow(BigDecimal lhs, BigDecimal rhs) {
        return lhs.add(rhs);       
    }
	
	public static int minus(int lhs, int rhs) { return lhs - rhs; }
	public static long minus(long lhs, long rhs) { return lhs - rhs; }
	public static float minus(float lhs, float rhs) { return lhs - rhs; }
	public static double minus(double lhs, double rhs) { return lhs - rhs; }
	
	public static Number minusUnderflow(int lhs, int rhs) {
        try {
            return Math.subtractExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return minusUnderflow((long)lhs, (long)rhs);
        }
    }
    public static Number minusUnderflow(long lhs, long rhs) {
        try {
            return Math.subtractExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return minusUnderflow(BigInteger.valueOf(lhs), BigInteger.valueOf(rhs));
        }
    }
    public static BigInteger minusUnderflow(BigInteger lhs, BigInteger rhs) {
        return lhs.subtract(rhs);       
    }

	public static int multiply(int lhs, int rhs) { return lhs * rhs; }
	public static long multiply(long lhs, long rhs) { return lhs * rhs; }
	public static float multiply(float lhs, float rhs) { return lhs * rhs; }
	public static double multiply(double lhs, double rhs) { return lhs * rhs; }
	
    public static Number multiplyOverflow(int lhs, int rhs) {
        try {
            return Math.multiplyExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return multiplyOverflow((long)lhs, (long)rhs);
        }
    }
    public static Number multiplyOverflow(long lhs, long rhs) {
        try {
            return Math.multiplyExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return multiplyOverflow(BigInteger.valueOf(lhs), BigInteger.valueOf(rhs));
        }
    }
    public static BigInteger multiplyOverflow(BigInteger lhs, BigInteger rhs) {
        return lhs.multiply(rhs);       
    }	
	
	public static int divide(int lhs, int rhs) { return lhs / rhs; }
	public static long divide(long lhs, long rhs) { return lhs / rhs; }
	public static float divide(float lhs, float rhs) { return lhs / rhs; }
	public static double divide(double lhs, double rhs) { return lhs / rhs; }
	
    public static Number divideOverflow(int lhs, int rhs) {
        try {
            return Math.divideExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return divideOverflow((long)lhs, (long)rhs);
        }
    }
    public static Number divideOverflow(long lhs, long rhs) {
        try {
            return Math.divideExact(lhs, rhs);
        } catch (ArithmeticException e) {
            return divideOverflow(BigInteger.valueOf(lhs), BigInteger.valueOf(rhs));
        }
    }
    public static BigInteger divideOverflow(BigInteger lhs, BigInteger rhs) {
        return lhs.divide(rhs);       
    }   	
    
    public static int negate(int n) { return -n; }
    public static long negate(long n) { return -n; }
    public static float negate(float n) { return -n; }
    public static double negate(double n) { return -n; }
    public static BigInteger negate(BigInteger n) { return n.negate(); }
    public static BigDecimal negate(BigDecimal n) { return n.negate(); }
    
    
	
	public static boolean numEquals(int lhs, int rhs) { return lhs == rhs; }
	public static boolean numEquals(long lhs, long rhs) { return lhs == rhs; }
	public static boolean numEquals(float lhs, float rhs) { return lhs == rhs; }
	public static boolean numEquals(double lhs, double rhs) { return lhs == rhs; }
	public static boolean numEquals(BigDecimal lhs, BigDecimal rhs) { return lhs.equals(rhs); }
	
	public static boolean lessThan(int lhs, int rhs) { return lhs < rhs; }
	public static boolean lessThan(long lhs, long rhs) { return lhs < rhs; }
	public static boolean lessThan(float lhs, float rhs) { return lhs < rhs; }
	public static boolean lessThan(double lhs, double rhs) { return lhs < rhs; }
	public static boolean lessThan(BigDecimal lhs, BigDecimal rhs) { return lhs.compareTo(rhs) < 0; }
	
	public static boolean greaterThan(int lhs, int rhs) { return lhs > rhs; }
	public static boolean greaterThan(long lhs, long rhs) { return lhs > rhs; }
	public static boolean greaterThan(float lhs, float rhs) { return lhs > rhs; }
	public static boolean greaterThan(double lhs, double rhs) { return lhs > rhs; }
	public static boolean greaterThan(BigDecimal lhs, BigDecimal rhs) { return lhs.compareTo(rhs) > 0; }
	
	public static boolean lessThanEquals(int lhs, int rhs) { return lhs <= rhs; }
	public static boolean lessThanEquals(long lhs, long rhs) { return lhs <= rhs; }
	public static boolean lessThanEquals(float lhs, float rhs) { return lhs <= rhs; }
	public static boolean lessThanEquals(double lhs, double rhs) { return lhs <= rhs; }
	public static boolean lessThanEquals(BigDecimal lhs, BigDecimal rhs) { return lhs.compareTo(rhs) <= 0; }
	
	public static boolean greaterThanEquals(int lhs, int rhs) { return lhs >= rhs; }
	public static boolean greaterThanEquals(long lhs, long rhs) { return lhs >= rhs; }
	public static boolean greaterThanEquals(float lhs, float rhs) { return lhs >= rhs; }
	public static boolean greaterThanEquals(double lhs, double rhs) { return lhs >= rhs; }
	public static boolean greaterThanEquals(BigDecimal lhs, BigDecimal rhs) { return lhs.compareTo(rhs) >= 0; }
	
	public static int compare(int lhs, int rhs) { return lhs < rhs ? -1 : lhs == rhs ? 0 : 1; }
    public static int compare(long lhs, long rhs) { return lhs < rhs ? -1 : lhs == rhs ? 0 : 1; }
    public static int compare(float lhs, float rhs) { return lhs < rhs ? -1 : lhs == rhs ? 0 : 1; }
    public static int compare(double lhs, double rhs) { return lhs < rhs ? -1 : lhs == rhs ? 0 : 1; }
    public static int compare(BigDecimal lhs, BigDecimal rhs) { return lhs.compareTo(rhs); }
    
    // Bits
    public static int bitAnd(int lhs, int rhs) { return lhs & rhs; }
    public static long bitAnd(long lhs, long rhs) { return lhs & rhs; }
    
    public static int bitOr(int lhs, int rhs) { return lhs | rhs; }
    public static long bitOr(long lhs, long rhs) { return lhs | rhs; }
    
    public static int bitXor(int lhs, int rhs) { return lhs ^ rhs; }
    public static long bitXor(long lhs, long rhs) { return lhs ^ rhs; }
    
    // shift
    // individual unary promotion
    public static int shiftLeft(int base, long distance) { return base << distance; }
    public static long shiftLeft(long base, long distance) { return base << distance; }
    public static int shiftRight(int base, long distance) { return base >> distance; }
    public static long shiftRight(long base, long distance) { return base >> distance; }
    public static int shiftRightLogical(int base, long distance) { return base >>> distance; }
    public static long shiftRightLogical(long base, long distance) { return base >>> distance; }
    
    // remainder
    public static int remainder(int lhs, int rhs) { return lhs % rhs; }
    public static long remainder(long lhs, long rhs) { return lhs % rhs; }
    public static float remainder(float lhs, float rhs) { return lhs % rhs; }
    public static double remainder(double lhs, double rhs) { return lhs % rhs; }
    
    
    public static Number negateSafe(int v) { if (v == Integer.MIN_VALUE) return -((long)v); else return -v; }
    public static Number negateSafe(long v) { if (v == Long.MIN_VALUE) return BigInteger.valueOf(v).negate(); else return -v; }
    // TODO Next two return types could be narrowed but not sure if that would break return typing.
    public static Number negateSafe(float v) { return -v; }
    public static Number negateSafe(double v) { return -v; } 
 
    public static Number negateSafe(BigInteger v) { return v.negate(); }
    public static Number negateSafe(BigDecimal v) { return v.negate(); } 

    //@formatter:on
    
    
}
