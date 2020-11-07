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

import static pile.compiler.Helpers.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import pile.util.Pair;

public class NumberHelpers {

    private static List<Class<?>> PRIMITIVE_ORDER = List.of(Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE,
            Double.TYPE);

    private static Set<Class<?>> PRIMITIVE_NUMBERS = Set.of(Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE,
            Double.TYPE);

    private static Map<Class<?>, Integer> INDEXES = new HashMap<>();

    static {
        int i = 0;
        for (; i < PRIMITIVE_ORDER.size(); ++i) {
            INDEXES.put(PRIMITIVE_ORDER.get(i), i);
        }
        INDEXES.put(BigInteger.class, i);
        INDEXES.put(BigDecimal.class, i + 1);
    }

    /**
     * 
     * @return A comparator which models the numeric promotion classes.
     */
    public static Comparator<Class<?>> getNumberPromotionComparator() {
        return Comparator.comparingInt(c -> INDEXES.get(toPrimitive(c)));
    }

    private static final TreeSet<Class<?>> DOUBLE_ONLY = new TreeSet<Class<?>>(getNumberPromotionComparator());
    private static final TreeSet<Class<?>> DFLI = new TreeSet<Class<?>>(getNumberPromotionComparator());
    private static final TreeSet<Class<?>> BBDFLI = new TreeSet<Class<?>>(getNumberPromotionComparator());
    private static final TreeSet<Class<?>> LI = new TreeSet<Class<?>>(getNumberPromotionComparator());
    private static final TreeSet<Class<?>> BLI = new TreeSet<Class<?>>(getNumberPromotionComparator());

    static {
        DFLI.add(Double.TYPE);
        DFLI.add(Float.TYPE);
        DFLI.add(Long.TYPE);
        DFLI.add(Integer.TYPE);
        
        BBDFLI.add(BigDecimal.class);
        BBDFLI.add(BigInteger.class);
        BBDFLI.add(Double.TYPE);
        BBDFLI.add(Float.TYPE);
        BBDFLI.add(Long.TYPE);
        BBDFLI.add(Integer.TYPE);
        
        LI.add(Long.TYPE);
        LI.add(Integer.TYPE);

        DOUBLE_ONLY.add(Double.TYPE);

        BLI.add(BigInteger.class);
        BLI.add(Long.TYPE);
        BLI.add(Integer.TYPE);
    }
    
    

    public static NavigableSet<Class<?>> getBLI() {
        return BLI;
    }

    public static boolean isNumberType(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return PRIMITIVE_NUMBERS.contains(clazz);
        } else {
            return Number.class.isAssignableFrom(clazz);
        }
    }

    public static TreeSet<Class<?>> getDoubleOnly() {
        return DOUBLE_ONLY;
    }

    public static NavigableSet<Class<?>> getDFLI() {
        return DFLI;
    }
    
    public static TreeSet<Class<?>> getBBDFLI() {
        return BBDFLI;
    }
    
    public static NavigableSet<Class<?>> getLI() {
        return LI;
    }
    
    public static Optional<Class<?>> promote(Class<?> source, NavigableSet<Class<?>> order) {
        return Optional.ofNullable(order.ceiling(source));
    }
    
    
    private static final Map<Class<?>, Integer> FIXED_WIDTH_INTEGRAL_NUMBER_PRECENDENCE,
            ARBITRARY_PRECISION_INTEGRAL_NUMBER_PRECEDENCE,
            FIXED_WIDTH_FLOAT_NUMBER_PRECENDENCE,
            ARBITRARY_PRECISION_FLOAT_NUMBER_PRECEDENCE,
            FIXED_WIDTH_METHODS_PRECEDENCE,
            JAVA_NUMERIC_PRECEDENCE
            ;
            
    public static final Set<Class<?>> ALL_FIXED_WIDTH, ALL_ARBITRARY_PRECISION;
    public static final List<Class<?>> ALL_FIXED, ALL_ARBITRARY;
    
    static {
        List<Class<?>> fixedWidthIntegral = Arrays.asList(Byte.class, Byte.TYPE, Short.class, Short.TYPE,
                Integer.class, Integer.TYPE, Long.class, Long.TYPE);
        var arbitraryPrecisionIntegral = new ArrayList<>(fixedWidthIntegral);
        arbitraryPrecisionIntegral.add(BigInteger.class);
        
        List<Class<?>> fixedWidthFloat = List.of(Float.class, Float.TYPE, Double.class, Double.TYPE);
        var arbitraryPrecisionFloat = new ArrayList<>(fixedWidthFloat);
        arbitraryPrecisionFloat.add(BigDecimal.class);
        
        List<Class<?>> allFixed = new ArrayList<>();
        allFixed.addAll(fixedWidthIntegral);
        allFixed.addAll(fixedWidthFloat);
        
        ALL_FIXED = Collections.unmodifiableList(allFixed);
        
        List<Class<?>> allArb = new ArrayList<>();
        allArb.addAll(arbitraryPrecisionIntegral);
        allArb.addAll(arbitraryPrecisionFloat);
        
        ALL_ARBITRARY = Collections.unmodifiableList(allArb);
        
        List<Class<?>> fixedMethodTypes = new ArrayList<>();
        fixedMethodTypes.addAll(allFixed);
        fixedMethodTypes.add(BigInteger.class);
        fixedMethodTypes.add(BigDecimal.class);
        
        FIXED_WIDTH_INTEGRAL_NUMBER_PRECENDENCE = Collections.unmodifiableMap(indexMap(fixedWidthIntegral));
        ARBITRARY_PRECISION_INTEGRAL_NUMBER_PRECEDENCE = Collections.unmodifiableMap(indexMap(arbitraryPrecisionIntegral));
        FIXED_WIDTH_FLOAT_NUMBER_PRECENDENCE = Collections.unmodifiableMap(indexMap(fixedWidthFloat));
        ARBITRARY_PRECISION_FLOAT_NUMBER_PRECEDENCE = Collections.unmodifiableMap(indexMap(arbitraryPrecisionFloat));
        FIXED_WIDTH_METHODS_PRECEDENCE = Collections.unmodifiableMap(indexMap(fixedMethodTypes));
        JAVA_NUMERIC_PRECEDENCE = Collections.unmodifiableMap(indexMap(allFixed));
        
        ALL_FIXED_WIDTH = Collections.unmodifiableSet(new HashSet<>(allFixed));
        ALL_ARBITRARY_PRECISION = Collections.unmodifiableSet(new HashSet<>(allArb));        
    }
        
    public static boolean isFixedWidthMethodType(Class clazz) {
        return FIXED_WIDTH_METHODS_PRECEDENCE.containsKey(clazz);
    }

    public static boolean isFixedWidthNumberType(Class clazz) {
        return ALL_FIXED_WIDTH.contains(clazz);
    }
    
    public static boolean isArbitraryPrecisionNumberType(Class clazz) {
        return ALL_ARBITRARY_PRECISION.contains(clazz);
    }
    
    public static boolean isFixedWidthIntegralType(Class clazz) {
        return FIXED_WIDTH_INTEGRAL_NUMBER_PRECENDENCE.containsKey(clazz);
    }
    
    public static boolean isArbitraryPrecisionIntegralType(Class clazz) {
        return ARBITRARY_PRECISION_INTEGRAL_NUMBER_PRECEDENCE.containsKey(clazz);
    }
    
    public static boolean isFixedWidthFloatType(Class clazz) {
        return FIXED_WIDTH_FLOAT_NUMBER_PRECENDENCE.containsKey(clazz);
    }
    
    public static boolean isArbitraryPrecisionFloatType(Class clazz) {
        return ARBITRARY_PRECISION_FLOAT_NUMBER_PRECEDENCE.containsKey(clazz);
    }
    
    public static Comparator<Class> getJavaNumericComparator() {
        return Comparator.comparingInt(JAVA_NUMERIC_PRECEDENCE::get);
    }
    
    public static Comparator<Class> getFixedWidthMethodComparator() {
        return Comparator.comparingInt(FIXED_WIDTH_METHODS_PRECEDENCE::get);
    }
    
    public static Comparator<Class> getFixedWidthIntegralComparator() {
        return Comparator.comparingInt(FIXED_WIDTH_INTEGRAL_NUMBER_PRECENDENCE::get);
    }
    
    public static Comparator<Class> getFixedWidthFloatComparator() {
        return Comparator.comparingInt(FIXED_WIDTH_FLOAT_NUMBER_PRECENDENCE::get);
    }
    
    public static Comparator<Class> getArbitraryPrecisionIntegralComparator() {
        return Comparator.comparingInt(ARBITRARY_PRECISION_INTEGRAL_NUMBER_PRECEDENCE::get);
    }
    
    public static Comparator<Class> getArbitraryPrecisionFloatComparator() {
        return Comparator.comparingInt(ARBITRARY_PRECISION_FLOAT_NUMBER_PRECEDENCE::get);
    }
    
}
