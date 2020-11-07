package pile.core.compiler;

import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import pile.collection.PersistentHashMap;
import pile.collection.PersistentList;
import pile.core.Metadata;
import pile.core.SettableRef;
import pile.core.binding.Binding;
import pile.core.binding.BindingType;
import pile.util.Pair;

public class CompilerBinding<T> implements Binding<T>, SettableRef<T> {
    //@formatter:off
    public static CompilerBinding<List<Pair<String,Object>>> LOCALS = new CompilerBinding<>("*locals*", new ArrayList<>()); 
    public static CompilerBinding<ClassWriter> CLASS_WRITER = new CompilerBinding<>("*class-writer*");
    public static CompilerBinding<MethodVisitor> METHOD_WRITER = new CompilerBinding<>("*method-writer*");
    public static CompilerBinding<List<String>> METHOD_LOCALS = new CompilerBinding<>("*method-locals*", new ArrayList<>()); 
    
    ;
    //@formatter:on

    private final ThreadLocal<T> threadLocal;
    private final String name;

    CompilerBinding(String name) {
        this.name = name;
        this.threadLocal = new ThreadLocal<>();
    }

    CompilerBinding(String name, T initial) {
        this.name = name;
        this.threadLocal = ThreadLocal.withInitial(() -> initial);
    }

    @Override
    public PersistentHashMap meta() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Metadata withMeta(PersistentHashMap newMeta) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SwitchPoint getSwitchPoint() {
        return null; // TODO??
    }

    @Override
    public BindingType getType() {
        return BindingType.DYNAMIC;
    }

    @Override
    public T deref() {
        return threadLocal.get();
    }

    @Override
    public void set(T newRef) {
        threadLocal.set(newRef);
    }

    public String getName() {
        return name;
    }

    @Override
    public String namespace() {
        return "pile.core";
    }

}