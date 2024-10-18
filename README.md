# Overview

Pile is a Lisp implementation that runs on the JVM. It targets Java 23 to take advantage of new Java features. 

```clojure
(defn fib [a b] (cons a (lazy-seq (fib b (+ a b)))))
(take 20 (fib 0 1))
;; (0 1 1 2 3 5 8 13 21 34 55 89 144 233 377 610 987 1597 2584 4181)
```

All syntactic and conceptual similarities with Clojure are intentional as it was the inspiration for this language, however no code was used. 

NB. This project is still being developed and should currently only be used for evaluation.

# Feature List

- [Lisp-1](#lisp-1)
- [Compiled](#compiled)
- [Namespaces](#namespaces)
- [Lexical Scoping](#lexical-scoping)
- [Closures](#closures)
- [Macros](#macros)
- [Persistent Collections](#persistent-collections)
- [First-Class Functions](#first-class-functions)
- [Destructuring](#destructuring)
- [Java Type Creation](#java-type-creation)
- [Generic Functions](#generic-functions)
- [Multimethods](#multimethods)
- [Static Typing (Optional)](#static-typing-optional)
- Lazy Sequences
- [Async/Await](#async-await)
- [Coroutines](#coroutines)
- [Java Interop](#java-interop)
- [Streams](#streams)
- [Functional Interface Integration](#functional-interface-integration)
- [First-Class Java Functions](#first-class-java-functions)
- [Arbitrary-precision arithmetic](#arbitrary-precision-arithmetic)
- [Text Blocks](#text-blocks)
- [Encapsulation](#encapsulation)
- [Condition System](#condition-system) (beta)
- [AOT Compilation](#aot-compilation) (beta)

# Running

Currently the only way to run the language is the repl which can be executed by running the 'repl' script at the root project level. This simply builds the project from source and then loads the repl. For history support and control sequences you should run the repl from something like emacs or launch the main method 'pile.repl.ReplMain' from your IDE.

# Documentation

- [Tutorial](docs/tutorial.org) (in progress)

# Feature Descriptions

## Lisp-1
There is no special syntax or distinction between functions and other values

```clojure
(defn choice [cval left-fun right-fun val] 
  (if cval (left-fun val) (right-fun val)))
```

## Compiled

All functions are always compiled to bytecode: 

```
pile.repl> (defn choice [cval left-fun right-fun val] (if cval (left-fun val) (right-fun val)))
[pile.core.compiler.ClassCompiler] TRACE: Compiled class:
// class version 59.0 (59)
// access flags 0x1
public class pile/core/runtime/fclass$anon$68 {


  // access flags 0x1
  public func$77(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
  @Lpile/core/anno/GeneratedMethod;()
    ALOAD 1
    INVOKESTATIC pile/core/compiler/Helpers.ifCheck (Ljava/lang/Object;)Z
    ICONST_1
    IF_ICMPNE L0
    ALOAD 2
    ALOAD 4
    INVOKEDYNAMIC opaque(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; [ ... ]
    GOTO L1
   L0
   FRAME SAME
    ALOAD 3
    ALOAD 4
    INVOKEDYNAMIC opaque(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; [ ... ]
   L1
   FRAME SAME1 java/lang/Object
    ARETURN
    MAXSTACK = 2
    MAXLOCALS = 5

  // access flags 0x1001
  public synthetic <init>()V
    ALOAD 0
    INVOKESPECIAL java/lang/Object.<init> ()V
    RETURN
    MAXSTACK = 1
    MAXLOCALS = 1
}

#'pile.repl/choice
```

## Namespaces

A namespace is a container of definitions of functions and values. 

```clojure
;; Set ns
(ns a.b.c)
;; Define a value in the current namespace
(def v 4)
;; refer to that value, looking it up in the current namespace
v
;; unambiguously refer to the value by providing the full namespace
a.b.c/v
```

## Lexical Scoping

Pile has lexical scoping for method arguments and locals. Values defined at the namespace level are effectively dynamically scoped as their bound values can change during the execution of the program.

```clojure
(def dvar 12)
(defn print-dvar [] (prn dvar))
(print-dvar)
;; 12
(set! dvar 55)
(print-dvar)
;; 55 
```

Vars can be defined as thread-local by annotating the var with ^:dynamic.
```clojure
(def ^:dynamic dvar 12)
```

## Closures

Created functions can close over their lexical environment allowing them to reference symbols defined outside their scope:

```clojure
(defn plus-some [x] (fn [y] (+ x y)))
(def plus-two (plus-some 2))
(plus-two 5)
;; 7
```

## Static Typing (Optional)

You can annotate symbols in certain contexts with types:
- Let bindings

```clojure
(let [^String s (some-str-fn)] ... )
```

- Method arguments

```clojure
(defn indexof [^String s n] (. s indexOf n))
(= 3 (indexof "foobar" "b"))
```

- Return types

```clojure
(defn returns-str ^String [] "foobar")
```

These types are strictly checked, and are not simply hints. For example, this will throw an a ClassCastException:

```clojure
(defn accepts-str [^String s] s)
(accepts-str 12) ;; Throws CCE
```

## Macros

A macro is simply a function that operates on the syntax of the language, and has some metadata that identifies it as a macro to the compiler.

Since these functions are purely syntax translations the macro system is non-hygenic. However, there are two features which mitigate possible identifier capture:
- Symbol Namespacing
- Auto-gensym

Macros have syntactic sugar for the four helpers:
- quote '
- syntax-quote `
- unquote ~
- unquote-splice ~@

## Persistent Collections

## First-Class Functions

Functions are full objects and can be stored in data structures and used as arguments to functions.

## First-Class Java Functions

Integer::valueOf is syntactic sugar that creates a first-class that calls the named function of the provided type. This means java methods can exist as first class functions:

```clojure
(Integer::valueOf "12") // 12
(map Number::longValue [1 2.2]) // (1L 2L)
```

The generated function can call any arity/type of the named method although typically it is going to be a single method target. This syntax can call either static or instance methods, however all named variants must be all static or all instance methods (eg. Integer::toString would fail because there are both instance and static methods of Integer named 'toString'). Under the hood that syntax is converted to a call to (java-method Integer "valueOf"). Constructors can be called using this syntax by using the method named 'new' similar to how Java method references work.

This function can be used in all the ways a function can:

```java
record Person(String fname, String lname, int age) {}
```

```clojure
(def info ["John" "H" 36])
(apply Person::new info) ;; Person(John, H, 36)

(def johns-only (partial Person::new "John"))
(johns-only "Smith" 44) ;; Person(John, Smith, 44)
```

## Java Interop

Pile also supports the clojure interop syntax:
- the dot form '.'
- constructor invocation with 'new'
- static method call '(String/format ... )'
- field access with '.-'

New Instance
```clojure
(new HashMap)
```

Get Field (static)
```clojure
;; (. class-symbol -member-symbol)
(. Integer -SIZE)
```

Get Field (instance)
```clojure
;; public static class TestField {
;;    public String foo = "bar";
;;}
;;(. instance-expr -field-symbol)
(. (new TestField) -foo)
```

Method Call (static)
```clojure
(. Integer parseInt "12")
(. Integer (parseInt "12"))
(.parseInt Integer "12")
```

Method Call (instance)
```clojure
;; (. instance-expr (method-symbol args*))
;; (. instance-expr method-symbol args*)
;; (.method-symbol instance-expr args*)
(. "foobar" indexOf "b")
(. "foobar" (indexOf "b"))
(.indexOf "foobar" "b")
```

All interop calls support calling vararg functions seamlessly:
```clojure
;; Interop calls
(String/format "This %s or that %s" 1 "one") 
;; Interop + mixed type/arity/varargs
(import java.nio.file.Path)
(Path/of "a")
(Path/of "a" "b")
(def file (new java.io.File "file.txt"))
(Path/of (-> file .toURI))
```

## Functional Interface Integration

Within Java interop it is possible to adapt Pile functions to implement java Functional Interfaces via the '~#' syntax.

```clojure
;; Calls the List.forEach default method with a Pile function adapted to be a java.util.function.Consumer.
pile.repl> (.forEach [1 2 3] ~#prn)
1
2
3
```

This also works for locals:
```clojure
pile.repl> (defn print-each [f] (.forEach [1 2 3] ~#f))
pile.repl> (print-each prn)
1
2
3
```

If the adapt syntax is used with an s-expr it is considered to be an anonymous function:
```clojure
pile.repl> (.forEach [1 2 3] ~#(prn "item: " %0))
item: 1
item: 2
item: 3
```

This adaptation works for all SAM types, not just java specific ones. 

This feature currently only works when the SAM type is unambiguous at compile time. This may change in the future to be more dynamic.

There is also support to convert SAM types into callable Pile methods with the pile.core/to-fn function. It accepts an instance of a SAM type and returns a callable function bound to that object calling that single method.

```clojure
pile.repl> (import java.util.Comparator)
pile.repl> (def java-cmp (Comparator/naturalOrder))
pile.repl> (def call-cmp (to-fn java-cmp))
pile.repl> (call-cmp 55 66)
;; -1
```

## Java Type Creation

Pile has several methods of creating types that extend base classes and/or implement interfaces.

### deftype

The deftype form defines a named class implementing statically known supertype & interfaces with no closed over values. This form has several parts:
- Type Name
- Type Constructor arguments
- Implemented supertype (0 or 1) and/or interfaces (0 to many)
  If the supertype is specified it *must* be followed by a vector of constructor arguments.
- Method definitions

```clojure
;; Template
(deftype TypeName [type constructor arguments]
         Supertype [supertype constructor arguments]
         Interface0
         (ifacefn [this] ...)
         Interface1
         (otherfn [this a b] ...))
```

An empty iterator:
```clojure
(deftype EmptyIter [] 
         java.util.Iterator 
         (hasNext [this] false) 
         (next [this] (throw (java.util.NoSuchElementException.))))
```

A point in time which takes in an instant to return each invocation of instant:
```clojure
(deftype PointInTime [inst] 
         java.time.InstantSource (instant [this] inst))
(def p (PointInTime. (java.time.Instant/now)))
(.instant p)
```

Varargs methods are supported for implementation:
```clojure
;; public interface VariadicInterface { public String call(int num, String... strs); }
(deftype VarIntf []
    VariadicInterface
    (call [this num & strs] (apply str num strs)))
(. vi call 123 "a" "b" "c") ;; "123abc"
```

The vararg parameter ('strs' in the example above) may be treated like a sequence.

_Notes_

The order of the super-type/interface-types with the method definitions is not semantically relevant and can be in any order (with the exception that the supertype constructor arguments must follow the supertype itself) eg.

```clojure
(deftype T []
         Interface0
         Interface1
         (interface0-method [this] ...)
         Supertype [a b c]
         (interface1-method [this] ...))
```

While this is allowed it is preferred if the types precede their associated method definitions.

### anon-cls

The anon-cls form creates an anonymous instance implementing statically known supertype & interfaces and allows closed over values.

```clojure
(defn source []
      (let [inst (Instant/now)]
          (anon-cls java.time.InstantSource (instant [this] inst))))
```

### proxy

The proxy method creates an anonymous instance with dynamic interfaces and dynamically created method implementations. This method takes in a vector of interfaces to implement and a map from method name to either a function or a list of functions.

```clojure
(def p (proxy [java.time.InstantSource] {"instant" (fn [this] (java.time.Instant/now))}))
(.instant p) ;; #object[java.time.Instant@524241174 "<time repr>"]
;; default methods
(.millis p) ;; 1634455725692
```

## Generic Functions

Pile supports type-based multiple dispatch via generic functions.

Generic functions are defined with 'defgeneric', and typed implementations with 'defimpl'. 

```clojure
(defgeneric write-to [sink src])
(defimpl write-to [^PrintWriter sink ^String src] (.write sink src) (.flush sink))
(def pw (PrintWriter. System/-out))
(write-to pw "output")
;; "output"
```

Single dispatch variants can be inline specialized at a type definition (deftype) by adding :specialize within the definition followed by any number of specialized method implementations:

```clojure
(defgeneric tostr [t])
(deftype Stringable [s] 
	:specialize 
	(tostr [this] s))
(def s (Stringable. "1234"))
(tostr s)
;; "1234"
```

## Multimethods

Pile supports arbitrary multiple dispatch via multimethods. Use defmulti/defmethod to create/update multimethods.

```clojure
(defmulti getl (fn* [x] (get x :type)))
(defmethod getl :a [x] "a")
(defmethod getl :b [x] "b")
(defmethod getl :default [x] "default")

(= "a" (getl {:type :a}))
(= "b" (getl {:type :b}))
(= :default (getl {:type "idk"}))
```

Multimethods can use custom hierarchies if the keying function produces keywords. 

## Async/Await

Computation can be performed asynchronously on a virtual thread using async. Waiting for a single result is unified under deref/@.

```clojure
(defn run-parallel [x y]
  (let [slow-comp  (async (slow-computation x))
        other-comp (async (slower-computation y))]
      (use-results @slow-comp @other-comp)))
```

In some languages async is a viral function attribute and calling limitation. In Pile it is simply a macro. 

Waiting for the completion of one of multiple results is accomplished by using the (await ...) function. This function may wait on multiple things of different types to include:
- (async ...) tasks
- Channel gets
- Channel puts

```clojure
(await (async (do-compute)) get-channel [put-channel val-to-enqueue])
```

This await process is atomic and only one operation will succeed. 

## Coroutines

A coroutine can be created calling a particular function.  

```clojure
(defn call [] (prn "start!") (yield 1) (prn "middle") (yield 2) (prn "end"))
*0: #'pile.repl/call
pile.repl> (def c (coroutine call))
*1: #'pile.repl/c
pile.repl> (resume c)
start!
*2: 1
pile.repl> (resume c)
middle
*3: 2
pile.repl> (resume c)
end
;; Coroutine completed with no more values, so it returned nil after printing 'end'
pile.repl> (resume c)
pile.repl>
```

The function being called will initially be suspended but can be resumed and will execute until it yields a value, an exception is thrown or it naturally completes execution of the function.

## Destructuring

Pile supports both sequential and associative destructuring in both method arguments and let/loop definitions.

```clojure
(defn prefix-both [prefix both] 
      (let [[f s] both] 
           [(str prefix f) (str prefix s)]))
(prefix-both "pre" ["dawn" "mature"])
;; ["predawn" "premature"]

(defn prefix-both [prefix [f s]] 
      [(str prefix f) (str prefix s)])
(prefix-both "pre" ["historic" "tax"])
;; ["prehistoric" "pretax"]

```



## Streams

Pile supports stateful, lazy transformation streams. These operations take a source, a set of transformations and a terminal operation.

```clojure
(stream (range 10) (filter #(> % 5)) (map #(* % 3)) (into []))
;; [18 21 24 27]
```

## Arbitrary-precision arithmetic

Pile supports both fixed-precision and arbitrary-precision arithmetic. All the short operators perform fixed width arithmetic which can overflow or lose precision during unit conversion:

```clojure
(+ Long/-MAX_VALUE 1)
;; -9223372036854775808
```

You can use the alternate operators, which have a single quote suffix, to perform arbitrary-precision arithmetic:

```clojure
(+' Long/-MAX_VALUE 1)
;; 9223372036854775808
```

You can create arbitrary-precision integral literals with a 'N' suffix, and arbitrary-precision decimal literals with the 'b' suffix:

```clojure
(+' 0.1b 0.2b)
;; 0.3
```

All operations which would overflow or would lose precision are coerced to higher width or arbitrary precision types, depending on the context. 

## Text Blocks

Pile supports triple quoted strings called text blocks:

```clojure
(defn myfun
      """
      My
      Really
      Long
      Docs
      """
      [args] body)
```

In the above case the resulting string is "My\nReally\nLong\nDocs". Indentation is trimmed to the minimum indentation of any non-empty line.

## Encapsulation

The Language uses Java modules to encapsulate its own types. This prevents them from being accessed from their Java APIs while still allowing access from their related Pile functions. This allows the evolution of the language without needing to also manage the API surface of all the Java methods in the provided types.

```clojure
(def m {:a :b})

;; Interop attempting to call pile.collection.Counted.count() and failing
(.count m)
pile.core.exception.PileSyntaxErrorException: Unable to find INSTANCE method class pile.collection.SingleMap/count() to call [f:<repl>/l:1/c:1] 
    at pile.lang@0.0.1-SNAPSHOT/pile.compiler.form.InteropForm.makeError(InteropForm.java:514)
    at pile.lang@0.0.1-SNAPSHOT/pile.compiler.form.InteropForm.lambda$evaluateForm$3(InteropForm.java:191)
    at java.base/java.util.Optional.orElseThrow(Optional.java:403)
    at pile.lang@0.0.1-SNAPSHOT/pile.compiler.form.InteropForm.evaluateForm(InteropForm.java:191)
    at pile.lang@0.0.1-SNAPSHOT/pile.compiler.form.SExpr.evaluateForm(SExpr.java:190)
    at pile.lang@0.0.1-SNAPSHOT/pile.compiler.Compiler.evaluate(Compiler.java:64)
    at pile.lang@0.0.1-SNAPSHOT/pile.compiler.Compiler.evaluate(Compiler.java:54)
    at pile.lang@0.0.1-SNAPSHOT/pile.repl.ReplMain.run(ReplMain.java:88)
    at pile.lang@0.0.1-SNAPSHOT/pile.repl.ReplMain.main(ReplMain.java:130)

;; Calling the method which produces the count:
(count m)
;; 1

;; Access to exported Types and their functions is still available via interop
;; containsKey(Object) is defined in java.util.Map which is exported from module 'java.base'.
(.containsKey m :a)
;; true

(.get m :a)
;; :b

```

## Condition System

There is preliminary support for a condition system.

```clojure
;; Similar example in the common lisp wiki
(defn recip [v]
  (restart-case 
    (if (= v 0)
    	(error :on-zero)
    	(/ 1.0 v))
    (:return-zero [] 0)
    (:return-value [r] r)
    (:recalc-using [r] (recip r))))

(handler-bind [:on-zero ([] (invoke-restart :return-zero))]
	(recip 5)) 
;; .2   
	
(handler-bind [:on-zero ([] (invoke-restart :return-zero))]
	(recip 0))
;; 0
		
(handler-bind [:on-zero ([] (invoke-restart :return-value 44))]
	(recip 0)) 
;; 44

```

(restart-case body & case-statements)
This function wraps a body expression which it runs. The case statements labels are keywords, which can be individually referenced from an invoke-restart function, along with an argument list and body.

(error error-type & error-args)
This function triggers a lookup for bound handler functions (via handler-bind) of the same keyword type. This can also pass arguments to the bound handler function.

(handler-bind bindings & body)
Binds named handler functions which can be targeted from an error function. Handler names are keywords and can shadow earlier bound handlers.

(invoke-restart restart-case-name & args)
This function transfers control to a non-local named restart case and is typically called from within a bound handler function.

## AOT Compilation

There is preliminary support for AOT compiling code (currently just the standard library). You can create the AOT files with the 'aotgen' script. Then, run 'aotrepl' which uses these files.

# Footer

Copyright 2023 John Hinchberger
