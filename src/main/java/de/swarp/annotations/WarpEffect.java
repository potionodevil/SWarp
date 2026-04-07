package de.swarp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a visual/audio warp effect.
 * Allows the {@link de.swarp.effects.WarpEffectService}
 * to discover and register effects dynamically via reflection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WarpEffect {
    /** Unique effect identifier */
    String id();

    /** Human-readable display name */
    String displayName();
}
