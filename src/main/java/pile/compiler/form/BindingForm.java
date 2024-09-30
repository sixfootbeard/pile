package pile.compiler.form;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;
import static pile.core.indy.IndyHelpers.*;
import static pile.nativebase.NativeCore.*;
import static pile.util.CollectionUtils.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import pile.collection.PersistentList;
import pile.collection.PersistentVector;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MethodStack.TypeRecord;
import pile.compiler.Scopes.ScopeLookupResult;
import pile.core.BindingInvocation;
import pile.core.ISeq;
import pile.core.Namespace;
import pile.core.PCall;
import pile.core.RuntimeRoot;
import pile.core.Symbol;
import pile.core.Var;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.util.InvokeDynamicBootstrap;
import pile.util.Pair;

@SuppressWarnings("rawtypes")
public class BindingForm extends AbstractListForm {

    private static final PersistentVector EMPTY_ARGS = PersistentVector.EMPTY;
    private static final Symbol FN_SYM = new Symbol("pile.core", "fn");

    public BindingForm(PersistentList form) {
        super(form);
    }

    public BindingForm(PersistentList form, Namespace ns) {
        super(form, ns);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        return new DeferredCompilation(TypeTag.SEXP, compilerState, this::compile);
    }

    private void compile(CompilerState cs) {
        ISeq s = form.seq().next();
        Object bindingSyntax = s.first();
        ISeq fnSyntax = s.next();

        record Slot(Symbol v) {};

        List<Slot> varSyms = new ArrayList<>();
        Iterator<Pair<Object, Object>> pairs = pairIter(seq(bindingSyntax));
        int count = 1;
        while (pairs.hasNext()) {
            Pair<Object, Object> pair = pairs.next();
            Var var = getVar(cs, pair.left());
            Symbol varSym = var.asSymbol();
            varSyms.add(new Slot(varSym));
            Compiler.compile(cs, pair.right());
            ++count;
        }
        ISeq synFn = fnSyntax.cons(EMPTY_ARGS).cons(FN_SYM);
        Compiler.compile(cs, synFn);
        MethodVisitor mv = cs.getCurrentMethodVisitor();
        Object[] allSyms = mapA(varSyms, sa -> sa.v().toConst().get(), ConstantDynamic[]::new);
        List<TypeRecord> typeRecords = cs.getMethodStack().popN(count);
        Type[] stackTypes = getJavaTypeArray(typeRecords);
        indyVarArg(mv, "call", BindingForm.class, "bootstrap", Object.class, stackTypes, allSyms);
        cs.getMethodStack().push(Object.class);
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        ISeq s = form.seq().next();
        Object bindingSyntax = s.first();
        var fnSyntax = s.next().first();

        BindingInvocation invoke = new BindingInvocation();
        Iterator<Pair<Object, Object>> pairs = pairIter(seq(bindingSyntax));
        while (pairs.hasNext()) {
            Pair<Object, Object> pair = pairs.next();
            Var v = getVar(cs, pair.left());
            Object value = Compiler.evaluate(cs, pair.right());
            invoke = v.bindWith(invoke, value);
            // TODO Handle failures & reset state
        }
        return invoke.call((args) -> {
            return Compiler.evaluate(cs, fnSyntax);
        });
    }

    private Var getVar(CompilerState cs, Object o) {
        if (o instanceof Symbol s) {
            ScopeLookupResult slr = cs.getScope().lookupSymbolScope(s);

            if (slr == null) {
                throw new PileCompileException("Bad bind target:" + s, LexicalEnvironment.extract(o, this));
            }
            return switch (slr.scope()) {
                case NAMESPACE -> {
                    String namespace = slr.namespace();
                    String sym = slr.sym();
                    Namespace ns = RuntimeRoot.get(namespace);
                    yield VarForm.getIn(ns, sym);
                }
                default -> throw new PileCompileException("Bind target scope must be NAMESPACE, not" + slr.scope(),
                        LexicalEnvironment.extract(o, this));
            };
        } else {
            throw new PileCompileException("Expected symbol bind target", LexicalEnvironment.extract(o, this));
        }
    }

    @InvokeDynamicBootstrap
    public static CallSite bootstrap(Lookup caller, String method, MethodType type, Object... syms) throws Exception {
        // val_0, val_1, ... val_n, fn
        // bindingInvocation, val_0, ...
        // bindingInvocation, val_1, ...
        // bindingInvocation, fn
        // bindingInvocation.call(PCall)
        MethodHandle varBind = caller.findVirtual(Var.class, "bindWith",
                methodType(BindingInvocation.class, BindingInvocation.class, Object.class));
        MethodHandle tgt = caller.findVirtual(BindingInvocation.class, "call", methodType(Object.class, PCall.class));
        MethodHandle newBinding = caller.findConstructor(BindingInvocation.class, methodType(void.class));

        for (var o : syms) {
            Symbol sym = (Symbol) o;
            Namespace ns = RuntimeRoot.get(sym.getNamespace());
            Var var = VarForm.getIn(ns, sym.getName());

            MethodHandle boundVarBind = varBind.bindTo(var);

            tgt = collectArguments(tgt, 0, boundVarBind);
        }
        tgt = collectArguments(tgt, 0, newBinding);

        return new ConstantCallSite(tgt.asType(type));
    }

    public static String DOCUMENTATION = """
            Evaluate an expression in the context of multiple bound values. The values revert to their previous state once the expression has been evaluated

            (binding bound-value-vec expression)

            bound-value-vec is an even length vector of symbolic references and expressions to assign to those references.

            (def ^:dynamic ref 10)
            (defn run [a] (binding [ref a] (prn ref)))
            (run 15)
            ;; 15
            (prn ref)
            ;; 10

            The symbolic references must be NAMESPACE defined symbols and may be either plain, ^:dynamic or ^:scoped.
            Plain symbol's value changes will be visible to other threads and can be racy and should usually be avoided.

            """;

}
