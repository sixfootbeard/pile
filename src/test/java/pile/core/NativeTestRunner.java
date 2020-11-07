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

import java.lang.invoke.SwitchPoint;
import java.util.Collection;
import java.util.stream.Collectors;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import pile.collection.PersistentMap;
import pile.core.binding.NativeDynamicBinding;
import pile.core.binding.ThreadLocalBinding;

/**
 * Runs tests defined in a particular {@link TestNamespace pile namespace}. This
 * class makes available the function pile.test/*notifier* which allows the
 * following invocations:
 * <ul>
 * <li>(*notifier* :test-start [test-name])
 * <li>(*notifier* :test-success [test-name])
 * <li>(*notifier* :test-failure [test-name])
 * <li>(*notifier* :test-failure [test-name] [str-or-exception])
 * </ul>
 * 
 * The [test-name] may either be a string or a collection of strings which will
 * be concat'd together.
 * 
 *
 */
public class NativeTestRunner extends Runner implements Filterable {

    private static final String TEST_NOTIFIER_FN = "*notifier*";
    private static final String TEST_NAMESPACE = "pile.test";
    private final Keyword TEST_START = Keyword.of(null, "test-start");
    private final Keyword TEST_SUCCESS = Keyword.of(null, "test-success");
    private final Keyword TEST_FAIL = Keyword.of(null, "test-failure");

    private final String nsStr;
    private final Class<?> clazz;
    private final Description desc;

    public NativeTestRunner(Class<?> clazz) {
        this.clazz = clazz;
        this.nsStr = clazz.getAnnotation(TestNamespace.class).value();
        this.desc = Description.createSuiteDescription(clazz);
        collectMeta();
    }

    private void collectMeta() {

    }

    @Override
    public Description getDescription() {
        return desc;
    }

    @Override
    public void run(RunNotifier notifier) {
        notifier.fireTestSuiteStarted(getDescription());
        try {

            Namespace ns = RuntimeRoot.defineOrGet(TEST_NAMESPACE);

            PCall p = (args) -> {
                // kw test-name & args
                Keyword kw = (Keyword) args[0];
                String fullName;
                if (args[1] instanceof String s) {
                    fullName = s;
                } else {
                    Collection<?> c = (Collection) args[1];
                    fullName = c.stream().map(o -> o.toString()).collect(Collectors.joining(""));
                }
                Description desc = Description.createTestDescription(clazz, fullName);
                getDescription().addChild(desc);
                if (kw == TEST_START) {
                    // (*notifier* :test-start "test-name")
                    notifier.fireTestStarted(desc);
                } else if (kw == TEST_SUCCESS) {
                    // (*notifier* :test-success "test-name")
                    notifier.fireTestFinished(desc);
                } else if (kw == TEST_FAIL) {
                    // (*notifier* :test-failure "test-name")
                    // (*notifier* :test-failure "test-name" exception)
                    // (*notifier* :test-failure "test-name" failure-message)
                    Failure fail;
                    if (args.length == 2) {
                        fail = new Failure(desc, new AssertionError("Test failed: " + desc));
                    } else {
                        var exOrMsg = args[2];
                        if (exOrMsg instanceof Throwable ex) {
                            fail = new Failure(desc, ex);
                        } else {
                            AssertionError err = new AssertionError(exOrMsg.toString());
                            fail = new Failure(desc, err);
                        }
                    }
                    notifier.fireTestFailure(fail);
                } else {
                    throw new RuntimeException("Unexpected kw: " + kw);
                }
                return null;
            };

            var binding = new ThreadLocalBinding<>(TEST_NAMESPACE, TEST_NOTIFIER_FN, p, PersistentMap.empty(),
                    new SwitchPoint());
            ns.define(TEST_NOTIFIER_FN, binding);

            // Run tests by invoking require
            try {
                RuntimeRoot.require(nsStr);
            } catch (Throwable t) {
                Description desc = Description.createTestDescription(clazz, "EvaluateTestNamespace");
                notifier.fireTestFailure(new Failure(desc, t));
            }
        } finally {
            notifier.fireTestSuiteFinished(getDescription());
        }
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        System.out.println("Filter:" + filter);

    }

}
