package pile.core;

import java.lang.ScopedValue.Carrier;
import java.util.ArrayList;
import java.util.List;

import pile.core.exception.PileExecutionException;

public class BindingInvocation {

    private final Carrier carrier;
    private final List<VarVal> vars;

    public BindingInvocation() {
        this.carrier = null;
        this.vars = List.of();
    }

    private BindingInvocation(BindingInvocation other, Carrier carrier) {
        this.carrier = carrier;
        this.vars = other.vars;
    }

    public BindingInvocation(BindingInvocation other, Var var, Object varVal) {
        List<VarVal> copy = new ArrayList<>(other.vars);
        copy.add(new VarVal(var, varVal));
        this.carrier = other.carrier;
        this.vars = copy;
    }
    
    public Carrier getCarrier() {
        return carrier;
    }

    public BindingInvocation withCarrier(Carrier c) {
        return new BindingInvocation(this, c);
    }

    public BindingInvocation withVar(Var var, Object newValue) {
        return new BindingInvocation(this, var, newValue);
    }

    public Object call(PCall fn) throws Exception {
        List<VarVal> befores = null;
        if (! vars.isEmpty()) {
            befores = new ArrayList<>(vars.size());
            for (var pair : vars) {
                befores.add(new VarVal(pair.var(), pair.var().deref()));
            }
            for (var pair : vars) {
                pair.var().set(pair.val());
            }
        }
        try {
            if (carrier != null) {
                return carrier.call(() -> {
                    try {
                        return fn.invoke();
                    } catch (Exception e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new PileExecutionException(e);
                    }
                });
            } else {
                try {
                    return fn.invoke();
                } catch (Throwable e) {
                    throw new PileExecutionException(e);
                }
            }
        } finally {
            if (befores != null) {
                for (var after : befores) {
                    try {
                        after.var().set(after.val());
                    } catch (Throwable t) {
                        // TODO ERROR
                    }
                }
            
            }
        }
    }

    private record VarVal(Var var, Object val) {
    };

}
