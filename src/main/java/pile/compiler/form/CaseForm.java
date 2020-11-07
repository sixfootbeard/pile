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
import static pile.compiler.Helpers.*;
import static pile.util.CollectionUtils.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import pile.collection.PersistentList;
import pile.compiler.Compiler;
import pile.compiler.CompilerState;
import pile.compiler.DeferredCompilation;
import pile.compiler.MethodStack;
import pile.core.binding.IntrinsicBinding;
import pile.core.exception.PileCompileException;
import pile.core.parse.LexicalEnvironment;
import pile.core.parse.TypeTag;
import pile.nativebase.NativeCore;

public class CaseForm extends AbstractListForm {

    public CaseForm(PersistentList pl) {
        super(pl);
    }

    @Override
    public DeferredCompilation compileForm(CompilerState compilerState) {
        ParsedForm parsed = parse();
        return new DeferredCompilation(TypeTag.SEXP, IntrinsicBinding.CASE_FORM, cs -> {
            compile(cs, parsed);
        });
    }

    @Override
    public Object evaluateForm(CompilerState cs) throws Throwable {
        var parsed = parse();
        var val = Compiler.evaluate(cs, form.pop().head());
        for (var caseStmt : parsed.valueExprPairs()) {
            if (NativeCore.equals(val, caseStmt.reifiedConstant())) {
                return Compiler.evaluate(cs, caseStmt.valueExpr());
            }
        }
        return parsed.defaultExpr().orElseThrow(() -> new IllegalArgumentException());
    }

    private ParsedForm parse() {
        // 
        Object defaultForm = null;
        List<ParsedCaseStatement> statements = new ArrayList<>();
        
        Iterator it = form.pop().pop().iterator();
        while (it.hasNext()) {
            var first = it.next();
            if (it.hasNext()) {
                var second = it.next();
                statements.add(new ParsedCaseStatement(first, second));
            } else {
                defaultForm = first;
            }
        }
        return new ParsedForm(statements, Optional.ofNullable(defaultForm));
    }

    /**
     * Two level lookup here.
     * <pre>
     * (case x :a (do-a-thing) :b (do-b-thing) :c (do-c-thing))
     * 
     * ;; assuming hashes :a=0, :b=1, :c=0 (so collision at :a & :c)
     * int index = -1;
     * switch (hash(x)) {
     *    case 0:
     *      if (pile_equals(x, :a)) {
     *          index = 0;
     *      } else if (pile_equals(x, :c)) {
     *          index = 2;
     *      }
     *      break;
     *    case 1:
     *      if (pile_equals(x, :b) {
     *          index = 1;
     *      }
     *      break;
     * }
     * switch (index) {
     *    case -1: guess-Ill-just-die
     *    case 0: return (do-a-thing)
     *    case 1: return (do-b-thing)
     *    case 2: return (do-c-thing)
     * }
     * </pre>
     * @param cs
     * @param parts
     */
    private void compile(CompilerState cs, ParsedForm parts) {
        MethodStack stack = cs.getMethodStack();
        GeneratorAdapter ga = cs.getCurrentGeneratorAdapter();
        
        Object toCheck = this.form.pop().head();
        // (case <toCheck> ...)
        handleLineNumber(ga, toCheck);
        Compiler.compile(cs, toCheck);
        Class<?> topClass = stack.pop();
        ga.box(getType(topClass));
        Class<?> topClassWrapper = toWrapper(topClass);
        int caseTestInstance = ga.newLocal(getType(topClassWrapper));
        ga.storeLocal(caseTestInstance);
        
        int secondSwitchValue = ga.newLocal(INT_TYPE);
        ga.visitLdcInsn(-1);
        ga.storeLocal(secondSwitchValue);  // default -1

        
        record Target(Label label, Object targetExpr) {}
        List<Target> targets = new ArrayList<>();
        Map<Integer, List<CaseTuple>> hashToCase = new HashMap<>();
        int slot = 0;
        for (ParsedCaseStatement pair : parts.valueExprPairs()) {
            int fslot = slot;
            Label label = new Label();
            DeferredCompilation defer;
            try {
                defer = Compiler.compileDefer(cs, pair.reifiedConstant());
            } catch (Throwable e) {
                Optional<LexicalEnvironment> lex = LexicalEnvironment.extract(pair.reifiedConstant, form);
                throw new PileCompileException("Could not determine constant form", lex, e);
            }
            List<CaseTuple> cases = new ArrayList<>();
            if (defer.formType() == TypeTag.SEXP) {
                var list = expectList(pair.reifiedConstant());
                list.forEach(item -> cases.add(createSingle(defer, pair, fslot, item)));
            } else {
                cases.add(createSingle(defer, pair, fslot, pair.reifiedConstant()));
            }
            targets.add(new Target(label, pair.valueExpr()));  
            cases.forEach(ct -> {
                hashToCase.computeIfAbsent(ct.hash(), k -> new ArrayList<>()).add(ct);
            });
            ++slot;
        }
        
        Map<Integer, Label> hashToFirstLabel = new TreeMap<>();
        hashToCase.keySet().forEach(key -> hashToFirstLabel.put(key, new Label()));
        
        int uniqueHashCount = hashToFirstLabel.size();
        int[] hashes = new int[uniqueHashCount];
        Label[] firstLevelLabels = new Label[uniqueHashCount];
        int i = 0;
        for (var entry : hashToFirstLabel.entrySet()) {
            hashes[i] = entry.getKey();
            firstLevelLabels[i] = entry.getValue();
            
            ++i;
        }
        
        Label midPoint = new Label();
        Label endCase = new Label();

        // ~~ Gen
        // Call NativeCore.hash to get code
        MethodVisitor mv = cs.getCurrentMethodVisitor();
        
        if (!hashToCase.isEmpty()) {
            ga.loadLocal(caseTestInstance);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, getType(NativeCore.class).getInternalName(), "hash",
                    getMethodDescriptor(getType(Integer.TYPE), OBJECT_TYPE), false);
            // ..., <int>

            mv.visitLookupSwitchInsn(midPoint, hashes, firstLevelLabels);
            // ...,

            for (var entry : hashToCase.entrySet()) {
                List<CaseTuple> cases = entry.getValue();
                for (CaseTuple tuple : cases) {
                    Label firstLevelLabel = hashToFirstLabel.get(entry.getKey());
                    mv.visitLabel(firstLevelLabel);
                    Label elseLabel = new Label();
                    // load caseTestInstance
                    ga.loadLocal(caseTestInstance);
                    // ldc const form
                    mv.visitLdcInsn(tuple.condyValue());
                    if (tuple.reifiedConstant() instanceof Number) {
                        ga.box(getType(toPrimitive(tuple.reifiedConstant().getClass())));
                    }
                    // invokestatic Helpers.equals(<duped>, constForm)
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, getType(NativeCore.class).getInternalName(), "equals",
                            getMethodDescriptor(getType(Boolean.TYPE), OBJECT_TYPE, OBJECT_TYPE), false);
                    // ifeq GOTO ELSE;
                    mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
                    // GOTO midPoint
                    mv.visitLdcInsn(tuple.exprSlot());
                    ga.storeLocal(secondSwitchValue);
                    mv.visitJumpInsn(Opcodes.GOTO, midPoint);
                    // ELSE:
                    mv.visitLabel(elseLabel);
                    // <Recurs until defaultCase>
                }
                // Base case is just a goto to default
                mv.visitJumpInsn(Opcodes.GOTO, midPoint);
            }
        }
 
        // If we missed all parts then local should still be -1
        mv.visitLabel(midPoint);
        
        if (targets.size() > 0) {
            Label defaultCase = new Label();
            Label[] labelArr = targets.stream().map(Target::label).toArray(Label[]::new);

            ga.loadLocal(secondSwitchValue);
            mv.visitTableSwitchInsn(0, slot - 1, defaultCase, labelArr);

            for (var tgt : targets) {
                mv.visitLabel(tgt.label());
                handleLineNumber(mv, tgt.targetExpr());
                Compiler.compile(cs, tgt.targetExpr());
                // Each final expr eval pushes a value that we need to remove
                Class<?> targetType = stack.pop();
                if (targetType.isPrimitive()) {
                    ga.box(getType(targetType));
                }
                mv.visitJumpInsn(Opcodes.GOTO, endCase);
            }
            mv.visitLabel(defaultCase);
        }
        
        if (parts.defaultExpr().isPresent()) {
            Object defaultExpr = parts.defaultExpr().get();
            Compiler.compile(cs, defaultExpr);
            if (stack.peek().isPrimitive()) {
                Class<?> popped = stack.pop();
                ga.box(getType(popped));
                // not adding the boxed type back is effectively popping the boxed type since
                // the final stack element will be generic.
            }
        } else {
            // TODO Maybe string msg.
            Type iaeType = getType(IllegalArgumentException.class);
            String internalName = iaeType.getInternalName();
            mv.visitTypeInsn(Opcodes.NEW, internalName);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName, "<init>",
                    getMethodDescriptor(VOID_TYPE), false);
            mv.visitInsn(Opcodes.ATHROW);
        }
        
        mv.visitLabel(endCase);
        stack.pushAny();
    }
    
    private CaseTuple createSingle(DeferredCompilation defer, ParsedCaseStatement pair, int slot, Object value) {
        Object constForm = defer.ldcForm()
                .orElseThrow(() -> {
                    var lex = LexicalEnvironment.extract(value, form);
                    var ex = new PileCompileException("Case labels must have constant forms", lex);
                    throw ex;
                });
        int formHash = NativeCore.hash(pair.reifiedConstant());
        return new CaseTuple(pair.reifiedConstant(), constForm, pair.valueExpr(), formHash, slot);        
    }

    private record CaseTuple(Object reifiedConstant, Object condyValue, Object valueExpr, int hash, int exprSlot) {}
    private record ParsedCaseStatement(Object reifiedConstant, Object valueExpr) {}
    private record ParsedForm(List<ParsedCaseStatement> valueExprPairs, Optional<Object> defaultExpr) {}

}
