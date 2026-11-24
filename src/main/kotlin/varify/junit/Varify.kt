package varify.junit

import org.junit.jupiter.api.extension.*
import varify.event.*
import java.lang.annotation.Inherited
import kotlin.reflect.*

/**
 * Configures the varification of test methods and enables the [VarifyExtension] for them.
 *
 * Each setting is resolved separately from the first source that sets it:
 * 1. the test method,
 * 2. the test class (or its superclasses),
 * 3. the enclosing classes of a `@Nested` test class, from innermost to outermost,
 * 4. the JUnit configuration parameters `varify.variationAttemptsLimit`, `varify.rejectedAttemptsLimit`,
 *    `varify.shrinkAttemptsLimit`, and `varify.seed` (e.g. in `junit-platform.properties` or as system properties),
 * 5. the defaults of [varify.config.VarifyConfiguration].
 *
 * Event listeners are not overridden but collected: first the global listeners of the configuration parameter
 * `varify.eventListeners` (comma-separated class names, nested classes with `$`), then those of all annotations from
 * the outermost class to the test method. A listener class that appears several times is only used once, at its first
 * position.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
@ExtendWith(VarifyExtension::class)
annotation class Varify(
    /** The maximum number of variation attempts, or 0 if not set. */
    val variationAttemptsLimit: Int = 0,
    /** The maximum number of variation attempts rejected by failed assumptions, or 0 if not set. */
    val rejectedAttemptsLimit: Int = 0,
    /** The maximum number of shrinking attempts, or 0 if not set. */
    val shrinkAttemptsLimit: Int = 0,
    /** The random seed, or empty if not set. A string because every `Long` is a valid seed. */
    val seed: String = "",
    /**
     * Event listener classes. Each execution of a test method uses new instances because listeners may be stateful,
     * except for Kotlin objects. Classes must have a constructor without (required) parameters.
     */
    val eventListeners: Array<KClass<out VarifyEventListener>> = [],
)
