package pile.nativebase;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import pile.core.method.LinkableMethod;

/**
 * Instead of linking to the static method, link to the method returned by
 * calling the static method. That method must take no arguments and return a
 * {@link LinkableMethod}.
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface IndirectMethod {

}
