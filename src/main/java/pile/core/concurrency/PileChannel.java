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
package pile.core.concurrency;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import pile.core.Ref;

public interface PileChannel extends Ref, Closeable {

    public void put(Object val) throws InterruptedException;

    /**
     * Non-blocking put
     * 
     * @param acceptor
     * @param o
     * @return True if the object is 'handled' by the channel now, false if the
     *         object was rejected and still requires handling.
     */
    public void put(Supplier<Boolean> acceptor, Object o);

    public void get(Function<Object, Boolean> acceptor);

    public default Object get() throws InterruptedException, ExecutionException {
        CompletableFuture f = new CompletableFuture<>();
        get(f::complete);
        return f.get();
    }

    @Override
    default Object deref(long time, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture f = new CompletableFuture<>();
        get(f::complete);
        return f.get(time, unit);
    }

    @Override
    default Object deref() throws InterruptedException, ExecutionException {
        return get();
    }

}
