package pile.core.compiler;

import org.objectweb.asm.MethodVisitor;

public interface Form {

    void compileForm(MethodVisitor method);

    Object evaluateForm();

}