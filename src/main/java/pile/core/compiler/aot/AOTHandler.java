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
package pile.core.compiler.aot;

import static java.lang.invoke.MethodType.*;
import static java.util.Objects.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static org.objectweb.asm.Type.getMethodType;
import static pile.compiler.Helpers.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import pile.compiler.ClassCompiler;
import pile.core.CoreConstants;
import pile.core.runtime.generated_classes.LookupHolder;

/**
 * This class manages the AOT compilation. <br>
 * <br>
 * 
 * We're creating a synthetic class containing class names of compile functions
 * in our namespaces. The class will roughly look like:
 * 
 * <pre>
 * package pile.core.runtime;
 * 
 * class AOT {
 *     private static final Map<MethodKey, String> METHODS = new HashMap<>();
 *     static {
 *         // Each call to (def ...) some function would result in a
 *         // compilation in the open cinit here to something like
 *         METHODS.put(new MethodKey("pile.core", "map"), "pile.core.runtime.Map$1");
 *     }
 * }
 * </pre>
 * 
 * We can then load the CDS archive of this class. When the compiler is
 * evaluating (def map ...) in "pile.core" it can just shortcut load
 * "pile.core.runtime.Map$1" which should also be in the archive too.
 *
 */
public class AOTHandler {

    private static final String AOT_GEN_CLASS_NAME = "$AOT";
    
    private static final String MAP_FIELD_NAME = "METHODS";
    private static final AOTType AOT_TYPE;
    private static final String AOT_DIR;
    
    public enum AOTType { READ, WRITE, NONE; }

    static {
        var state = System.getProperty("pile.aot", "none");
        AOT_TYPE = AOTType.valueOf(state.toUpperCase());
        AOT_DIR = System.getProperty("pile.aot.dir");
    }

    static final String CLASSNAME = CoreConstants.GEN_PACKAGE + "/" + AOT_GEN_CLASS_NAME;
    private static final String CLASS_DESCRIPTOR = "L" + CLASSNAME + ";";
    private static final String INTERNAL_NAME = getType(CLASS_DESCRIPTOR).getInternalName();
    
    enum WrittenState { ONCE, DISABLED; }
    private static final Map<MethodKey, WrittenState> WRITTEN = new ConcurrentHashMap<>();

    public static void writeAotFunction(String ns, String fname, Class<?> clz) throws Exception {
        MethodKey key = new MethodKey(ns, fname);
        WrittenState state = WRITTEN.get(key);
        if (state == WrittenState.DISABLED) {
            return;
        }
        var m = WriteHolder.METHOD;
        
        m.getStatic(getType(CLASS_DESCRIPTOR), MAP_FIELD_NAME, getType(Map.class));
        // (map)
        
        Type mtype = getType(MethodKey.class);
        // new
        m.newInstance(mtype);
        // dup
        m.dup();
        // args
        m.visitLdcInsn(ns);
        m.visitLdcInsn(fname);
        m.invokeConstructor(getType(MethodKey.class),
                Method.getMethod(MethodKey.class.getConstructor(String.class, String.class)));
        // invokespecial
        // (map, methodKey)
        
        if (state == null) { 
            WRITTEN.put(key, WrittenState.ONCE);
            
            m.visitLdcInsn(clz.getName());
            // (map, methodKey, className)
            m.invokeInterface(getType(Map.class), Method.getMethod(Map.class.getMethod("put", Object.class, Object.class)));
            m.pop();
        } else if (state == WrittenState.ONCE) {
            WRITTEN.put(key, WrittenState.DISABLED);
            
            m.invokeInterface(getType(Map.class), Method.getMethod(Map.class.getMethod("remove", Object.class)));
            m.pop();
        } else {
            throw new UnsupportedOperationException();
        }
            

        
    }
    
    public static void writeAOTClass(String packageName, String clazz, byte[] classArray) {        
        String deref = AOTHandler.getAotDir();
        requireNonNull(deref, "AOT Dir must be set");
        
        String dir = packageName.replace('.', '/');
        Path path = Paths.get(deref, dir, clazz + ".class");        
        
        System.out.println(String.format("Writing AOT class '%s' to %s", clazz, path));
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, classArray);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write to " + path);
        }
        
    }

    public static void closeAOT() throws Exception {
        var ga = WriteHolder.METHOD;
        ga.visitInsn(Opcodes.RETURN);
        ga.visitMaxs(0, 0);
        ga.visitEnd();

        var writer = WriteHolder.WRITER;
        writer.visitEnd();

        byte[] classArray = writer.toByteArray();
        
        ClassCompiler.printDebug(classArray);
        
        writeAOTClass(CoreConstants.GEN_PACKAGE_DOT, AOT_GEN_CLASS_NAME, classArray);
        
        LookupHolder.LOOKUP.defineClass(classArray);
    }

    public static AOTType getAotType() {
        return AOT_TYPE;
    }
    
    public static String getAotDir() {
        return AOT_DIR;
    }

    // Reading

    public static Class<?> getAotFunctionClass(String ns, String name) throws ClassNotFoundException {
        String maybeClassStr = ReadHolder.READ_CLASSES.get(new MethodKey(ns, name));
        if (maybeClassStr == null) {
            return null; 
        } else {
            return Class.forName(maybeClassStr);        
        }        
    }

    @SuppressWarnings("unused")
    private static final class WriteHolder {
        private static final String STATIC_INIT_FN_NAME = "<clinit>";
        private static ClassWriter WRITER;
        private static GeneratorAdapter METHOD;

        static {
            WRITER = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            

            ClassVisitor visitor = WRITER;
            // private static Map METHODS;
            visitor.visitField(ACC_PUBLIC | ACC_STATIC, MAP_FIELD_NAME, getType(Map.class).getDescriptor(), null, null).visitEnd();
            


            visitor.visit(Opcodes.V15, ACC_PUBLIC, INTERNAL_NAME, null, OBJECT_TYPE.getInternalName(), null);
            var methodVisitor = visitor.visitMethod(ACC_PUBLIC | ACC_STATIC, STATIC_INIT_FN_NAME,
                    methodType(void.class).descriptorString(), null, null);
            GeneratorAdapter gen = new GeneratorAdapter(methodVisitor, ACC_PUBLIC | ACC_STATIC, STATIC_INIT_FN_NAME,
                    getMethodType(VOID_TYPE).getDescriptor());
                    
            // static { METHODS = new HashMap(); }
            try {
                gen.visitTypeInsn(Opcodes.NEW, getType(ConcurrentHashMap.class).getInternalName());
                gen.visitInsn(Opcodes.DUP);
                gen.invokeConstructor(getType(ConcurrentHashMap.class), Method.getMethod(ConcurrentHashMap.class.getConstructor()));
            } catch (NoSuchMethodException | SecurityException e) {
                throw new IllegalArgumentException("Shouldn't happen", e);
            }
            gen.putStatic(getType(CLASS_DESCRIPTOR), MAP_FIELD_NAME, getType(Map.class));
                

            METHOD = gen;
        }
    }

    public static final record MethodKey(String ns, String className) {
    }

    // Reading

    private static final class ReadHolder {
        private static final Map<MethodKey, String> READ_CLASSES;
        static {
            Map<MethodKey, String> local = new HashMap<>();
            try {
                Class<?> clazz = Class.forName(CoreConstants.GEN_PACKAGE_DOT + "." + AOT_GEN_CLASS_NAME);
//                System.out.println("fields:" + Arrays.toString(clazz.getFields()));
                Field field = clazz.getField(MAP_FIELD_NAME);
                local.putAll((Map<MethodKey, String>) field.get(null));
            } catch (ClassNotFoundException | IllegalArgumentException | IllegalAccessException | NoSuchFieldException
                    | SecurityException e) {
//                e.printStackTrace(System.err);
//                System.err.print("AOT didn't load or doesn't exist");
            }
//            System.out.println("AOT loaded map:" + local);
            READ_CLASSES = Collections.synchronizedMap(local);
        }
    }
}
