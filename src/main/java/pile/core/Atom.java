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
package pile.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Atom<T> implements SettableRef<T> {

	private final AtomicReference<T> ref;

	public Atom() {
		ref = new AtomicReference<>();
	}

	public Atom(T t) {
		ref = new AtomicReference<>(t);
	}

	@Override
	public void set(T newRef) {
		ref.set(newRef);
	}

	@Override
	public T deref() {
		return ref.get();
	}
	
	@Override
	public T deref(long time, TimeUnit unit) {
	    return deref();
	}

	@Override
	public void update(PCall fn) throws Throwable {
		for (;;) {
			T local = ref.get();
			T out = (T) fn.invoke(local);
			if (ref.compareAndSet(local, out)) {
				break;
			}
		}
	}

}
