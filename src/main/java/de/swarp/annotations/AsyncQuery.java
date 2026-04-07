package de.swarp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that a method performs a database query and must
 * be called from an async context — never from the main thread.
 * Used as compile-time documentation and can be checked
 * programmatically via reflection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AsyncQuery {
    /** Human-readable description of what the query does */
    String value() default "";
}
