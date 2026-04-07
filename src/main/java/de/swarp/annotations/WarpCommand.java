package de.swarp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a sub-command handler.
 * The CommandDispatcher uses this to automatically route
 * /swarp <sub> to the correct method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WarpCommand {
    /** The sub-command label, e.g. "create", "delete" */
    String value();

    /** Minimum number of additional args required */
    int minArgs() default 0;

    /** Usage hint shown on wrong usage */
    String usage() default "";

    /** Required permission node */
    String permission() default "";
}
