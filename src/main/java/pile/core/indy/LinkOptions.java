package pile.core.indy;

import pile.core.ISeq;
import pile.core.PCall;

public enum LinkOptions {

    /**
     * Ignore static var annotations. Typically for a static var it can be read once
     * the first time the callsite needs to know what to call and then is not read
     * again by that site. IE. For functions always compose the
     * {@link Var#getSwitchPoint() var switchpoints} into the method handles.
     */
    IGNORE_STATIC_METADATA,

    /**
     * For calls out to typed Java land, adapt {@link PCall} arguments to match
     * functional interfaces. EG.
     * 
     * <pre>
     * (-> widgets
     *     .stream
     *     (.filter #(= (.-getColor %) RED)
     *     (.mapToInt #(.-getWeight %))
     *     .sum)
     * </pre>
     * 
     */
    ADAPT_FUNCTIONAL_INTERFACE,

    /**
     * Adapt call to match argument sizes by expecting the last arg to be a
     * {@link ISeq} (maybe?). Typically only referenced by (apply ...)
     */
    VARIADIC_CALL_SITE,

    /**
     * Allow updating a var containing a macro to propagate when changed. Beta.
     */
    DYNAMIC_MACROS;

    public long getPosition() {
        return (1 << this.ordinal());
    }

    public boolean isSet(long options) {
        return (getPosition() & options) > 0;
    }

    public long set(long options) {
        return (getPosition() | options);
    }

}
