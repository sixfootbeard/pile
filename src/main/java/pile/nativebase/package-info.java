/**
 * A native base is a class with static methods which will be loaded into a
 * particular namespace as if it had been bootstrapped and written in pure lisp
 * code. This means there is no linkage boilerplate necessary to call into these
 * java methods. EG. pile.nativebase.NativeCore is loaded into the "pile.core"
 * namespace and all static methods are available from lisp code. The methods
 * provided can be overloaded and the most appropriate method will be called at
 * runtime.
 */
package pile.nativebase;