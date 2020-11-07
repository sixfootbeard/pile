package pile.nativebase;

import static pile.nativebase.NativeCore.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import pile.core.ISeq;

public class NativeArrays {

    // Arrays
    public static Object[] toArray(Collection<?> col) {
        return col.toArray();
    }

    @Precedence(0)
    public static <T> T[] aclone(T[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(1)
    public static char[] aclone(char[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(2)
    public static short[] aclone(short[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(3)
    public static int[] aclone(int[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(4)
    public static long[] aclone(long[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(5)
    public static float[] aclone(float[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(6)
    public static double[] aclone(double[] t) {
        return Arrays.copyOf(t, t.length);
    }

    @Precedence(0)
    public static <T> T aget(T[] t, int idx) {
        return t[idx];
    }

    @Precedence(1)
    public static char aget(char[] t, int idx) {
        return t[idx];
    }

    @Precedence(2)
    public static short aget(short[] t, int idx) {
        return t[idx];
    }

    @Precedence(3)
    public static int aget(int[] t, int idx) {
        return t[idx];
    }

    @Precedence(4)
    public static long aget(long[] t, int idx) {
        return t[idx];
    }

    @Precedence(5)
    public static float aget(float[] t, int idx) {
        return t[idx];
    }

    @Precedence(6)
    public static double aget(double[] t, int idx) {
        return t[idx];
    }

    @Precedence(0)
    public static <T> void aset(T[] t, int idx, T arg) {
        t[idx] = arg;
    }

    @Precedence(1)
    public static void aset(char[] t, int idx, char arg) {
        t[idx] = arg;
    }

    @Precedence(2)
    public static void aset(short[] t, int idx, short arg) {
        t[idx] = arg;
    }

    @Precedence(3)
    public static void aset(int[] t, int idx, int arg) {
        t[idx] = arg;
    }

    @Precedence(4)
    public static void aset(long[] t, int idx, long arg) {
        t[idx] = arg;
    }

    @Precedence(5)
    public static void aset(float[] t, int idx, float arg) {
        t[idx] = arg;
    }

    @Precedence(6)
    public static void aset(double[] t, int idx, double arg) {
        t[idx] = arg;
    }

    @Precedence(0)
    public static <T> int alength(T[] t) {
        return t.length;
    }

    @Precedence(1)
    public static int alength(char[] t) {
        return t.length;
    }

    @Precedence(2)
    public static int alength(short[] t) {
        return t.length;
    }

    @Precedence(3)
    public static int alength(int[] t) {
        return t.length;
    }

    @Precedence(4)
    public static int alength(long[] t) {
        return t.length;
    }

    @Precedence(5)
    public static int alength(float[] t) {
        return t.length;
    }

    @Precedence(6)
    public static int alength(double[] t) {
        return t.length;
    }

    @Precedence(0)
    public static boolean[] boolean_array(int size) {
        return new boolean[size];
    }

    @Precedence(1)
    public static boolean[] boolean_array(Object rseq) {
        List<Boolean> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Boolean) o));
        boolean[] out = new boolean[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static boolean[] boolean_array(int size, boolean init) {
        boolean[] arr = new boolean[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static boolean[] boolean_array(int size, Object init) {
        boolean[] arr = new boolean[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (boolean) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static byte[] byte_array(int size) {
        return new byte[size];
    }

    @Precedence(1)
    public static byte[] byte_array(Object rseq) {
        List<Byte> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Byte) o));
        byte[] out = new byte[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static byte[] byte_array(int size, byte init) {
        byte[] arr = new byte[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static byte[] byte_array(int size, Object init) {
        byte[] arr = new byte[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (byte) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static char[] char_array(int size) {
        return new char[size];
    }

    @Precedence(1)
    public static char[] char_array(Object rseq) {
        List<Character> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Character) o));
        char[] out = new char[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static char[] char_array(int size, char init) {
        char[] arr = new char[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static char[] char_array(int size, Object init) {
        char[] arr = new char[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (char) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static short[] short_array(int size) {
        return new short[size];
    }

    @Precedence(1)
    public static short[] short_array(Object rseq) {
        List<Short> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Short) o));
        short[] out = new short[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static short[] short_array(int size, short init) {
        short[] arr = new short[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static short[] short_array(int size, Object init) {
        short[] arr = new short[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (short) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static int[] int_array(int size) {
        return new int[size];
    }

    @Precedence(1)
    public static int[] int_array(Object rseq) {
        List<Integer> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Integer) o));
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static int[] int_array(int size, int init) {
        int[] arr = new int[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static int[] int_array(int size, Object init) {
        int[] arr = new int[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (int) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static long[] long_array(int size) {
        return new long[size];
    }

    @Precedence(1)
    public static long[] long_array(Object rseq) {
        List<Long> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Long) o));
        long[] out = new long[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static long[] long_array(int size, long init) {
        long[] arr = new long[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static long[] long_array(int size, Object init) {
        long[] arr = new long[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (long) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static float[] float_array(int size) {
        return new float[size];
    }

    @Precedence(1)
    public static float[] float_array(Object rseq) {
        List<Float> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Float) o));
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static float[] float_array(int size, float init) {
        float[] arr = new float[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static float[] float_array(int size, Object init) {
        float[] arr = new float[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (float) o;
            ++idx;
        }
        return arr;
    }

    @Precedence(0)
    public static double[] double_array(int size) {
        return new double[size];
    }

    @Precedence(1)
    public static double[] double_array(Object rseq) {
        List<Double> list = new ArrayList<>();
        ISeq seq = seq(rseq);
        ISeq.iter(seq).forEach(o -> list.add((Double) o));
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            out[i] = list.get(i);
        }
        return out;
    }

    @Precedence(2)
    public static double[] double_array(int size, double init) {
        double[] arr = new double[size];
        Arrays.fill(arr, init);
        return arr;
    }

    @Precedence(3)
    public static double[] double_array(int size, Object init) {
        double[] arr = new double[size];
        ISeq seq = seq(init);
        int idx = 0;
        for (Object o : ISeq.iter(seq)) {
            arr[idx] = (double) o;
            ++idx;
        }
        return arr;
    }
}
