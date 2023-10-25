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
package pile.core.indy;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static pile.compiler.Helpers.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import pile.core.PCall;
import pile.core.PileMethod;
import pile.core.exception.PileInternalException;
import pile.core.indy.guard.Guards;
import pile.core.method.AbstractRelinkingCallSite;

/**
 * A {@link CallSite} which advances through different caching strategy stages
 * before becoming unoptimized. Typically indy callsites contain
 * {@link CompilerFlags} which configure how these stages operate. Only the
 * unoptimized method generator needs to be implemented and is typically
 * {@link PCall#invoke(Object...)}. Each relink within a valid stage increases a
 * counter until a maximum number of relinks in that stage have been encounter
 * at which point the callsite advances to the next caching strategy.
 * 
 *
 */
public abstract class InlineCacheCallSite extends AbstractRelinkingCallSite {

    enum Stage {
        COLD, MONO, POLY, MEGA, UNOPTIMIZED;

        public Stage next() {
            return values()[this.ordinal() + 1];
        }
    }

    protected final CompilerFlags flags;
    private final int maxColdCalls;

    // protected by synchronized(this)
    private Stage stage = null;    
    // protected by synchronized(this)
    private int count = 0;

    public InlineCacheCallSite(MethodType type, int maxColdCalls, CompilerFlags flags) {
        super(type);
        this.flags = flags;
        this.maxColdCalls = maxColdCalls;
    }

    @Override
    protected synchronized final MethodHandle findHandle(Object[] args) throws Throwable {
        MethodType actualMethodTypes = getMethodTypeFromArgs(args);
        if (stage == null && maxColdCalls > 0) {
            stage = Stage.COLD;
            MethodHandle cold = makeCold(args, type());
            if (cold != null) {
                return Guards.maxCall(maxColdCalls, cold, relink);
            }
        }

        
        var current = getStage();
        if (++count >= getMaxStageCount(current)) {
            current = current.next();
            count = 0;
        }
        
        var next = current;
        while (next != Stage.UNOPTIMIZED) {
            if (isAllowed(next)) {
                var handle = switch (next) {
                    case MONO -> makeMono(args, actualMethodTypes);
                    case POLY -> makePoly(args, actualMethodTypes);
                    case MEGA -> makeMega(args, actualMethodTypes);
                    default -> throw new PileInternalException("Uncovered optimization: " + current);
                };
                if (handle != null) {
                    stage = next;
                    return handle;
                    
                }
            }
            next = next.next();
            count = 0;
            // else don't make an opti for this stage.
        }
        return makeUnopt(args, actualMethodTypes);
    }
    
    protected Stage getStage() {
        return stage;
    }
    
    protected MethodHandle makeCold(Object[] args, MethodType methodType) throws Throwable {
        return null;
    }

    protected abstract MethodHandle makeUnopt(Object[] args, MethodType methodType) throws Throwable;

    protected MethodHandle makePoly(Object[] args, MethodType methodType) throws Throwable {
        return null;
    }

    protected MethodHandle makeMega(Object[] args, MethodType methodType) throws Throwable {
        return null;
    }

    protected MethodHandle makeMono(Object[] args, MethodType methodType) throws Throwable {
        return null;
    }

    private int getMaxStageCount(Stage current) {
        return switch (current) {
            case COLD -> -1;
            case MONO -> flags.monomorphicMissThreshold();
            case POLY -> flags.polymorphicChainThreshold();
            case MEGA -> flags.megamorphicSizeThreshold();
            case UNOPTIMIZED -> 0;
        };
    }

    private boolean isAllowed(Stage current) {
        return switch (current) {
            case COLD -> true;
            case MONO -> flags.monomorphicMissThreshold() > 0;
            case POLY -> flags.polymorphicChainThreshold() > 0;
            case MEGA -> flags.megamorphicSizeThreshold() > 0;
            case UNOPTIMIZED -> true;
        };
    }

}
