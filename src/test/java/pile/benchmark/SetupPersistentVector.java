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
package pile.benchmark;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import pile.collection.PersistentVector;

@State(Scope.Benchmark)
public class SetupPersistentVector {


    @Param({ "5", "20", "100" })
    public int elemCount;


    public PersistentVector<Integer> list;

    @Setup(Level.Invocation)
    public void setUp() {
        list = PersistentVector.EMPTY;
        for (int i = 0; i < elemCount; ++i) {
            list = list.conj(i);
        }
    }
    
    
}