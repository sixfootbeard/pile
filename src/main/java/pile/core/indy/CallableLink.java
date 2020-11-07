package pile.core.indy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import pile.core.PCall;

/**
 * InvokeDynamic calls to an object with an annotated method of this type will
 * link to that method directly instead of indirectly through {@link PCall}.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface CallableLink {

}
