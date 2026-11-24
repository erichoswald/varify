package varify.junit

import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.*
import varify.config.*
import varify.event.*
import kotlin.reflect.*
import kotlin.reflect.full.*

private const val VARIATION_ATTEMPTS_LIMIT = "variationAttemptsLimit"
private const val REJECTED_ATTEMPTS_LIMIT = "rejectedAttemptsLimit"
private const val SHRINK_ATTEMPTS_LIMIT = "shrinkAttemptsLimit"
private const val SEED = "seed"
private const val EVENT_LISTENERS = "eventListeners"

/** Resolves the configuration of the current test method as described by [Varify]. */
internal fun ExtensionContext.varifyConfiguration(): VarifyConfiguration {
    val annotations = varifyAnnotations()
    val defaults = VarifyConfiguration()

    val variationAttemptsLimit =
        annotations.firstNotNullOfOrNull { it.variationAttemptsLimit.limitOrNull(VARIATION_ATTEMPTS_LIMIT) }
            ?: configurationLimit(VARIATION_ATTEMPTS_LIMIT)
            ?: defaults.variationAttemptsLimit
    val rejectedAttemptsLimit =
        annotations.firstNotNullOfOrNull { it.rejectedAttemptsLimit.limitOrNull(REJECTED_ATTEMPTS_LIMIT) }
            ?: configurationLimit(REJECTED_ATTEMPTS_LIMIT)
            ?: defaults.rejectedAttemptsLimit
    val shrinkAttemptsLimit =
        annotations.firstNotNullOfOrNull { it.shrinkAttemptsLimit.limitOrNull(SHRINK_ATTEMPTS_LIMIT) }
            ?: configurationLimit(SHRINK_ATTEMPTS_LIMIT)
            ?: defaults.shrinkAttemptsLimit
    val seed = configuredSeed(annotations)
    val eventListeners =
        // Global listeners first, then from the outermost class to the test method.
        (configurationEventListeners() + annotations.asReversed().flatMap { it.eventListeners.asList() })
            .distinct()
            .map { it.instantiate() }

    return defaults.copy(
        variationAttemptsLimit = variationAttemptsLimit,
        rejectedAttemptsLimit = rejectedAttemptsLimit,
        shrinkAttemptsLimit = shrinkAttemptsLimit,
        randomizer = seed?.let { Randomizer.System(it) } ?: defaults.randomizer,
        eventListeners = defaults.eventListeners + eventListeners,
    )
}

internal fun ExtensionContext.configuredSeed(annotations: List<Varify> = varifyAnnotations()): Long? =
    annotations.firstNotNullOfOrNull { it.seed.seedOrNull() }
        ?: configurationSeed()

private fun ExtensionContext.varifyAnnotations(): List<Varify> {
    val method = testMethod.flatMap { AnnotationSupport.findAnnotation(it, Varify::class.java) }
    val classes = listOf(requiredTestClass) + enclosingTestClasses.reversed() // Traverse innermost to outermost.
    val classAnnotations = classes.map { AnnotationSupport.findAnnotation(it, Varify::class.java) }
    return (listOf(method) + classAnnotations).mapNotNull { it.orElse(null) }
}

private fun ExtensionContext.configurationEventListeners(): List<KClass<out VarifyEventListener>> =
    getConfigurationParameter("varify.$EVENT_LISTENERS").orElse(null)
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.map(::eventListenerClass)
        .orEmpty()

private fun eventListenerClass(className: String): KClass<out VarifyEventListener> {
    val loadedClass = ReflectionSupport.tryToLoadClass(className).toOptional().orElse(null)
        ?: throw VarifyConfigurationException("Configuration parameter varify.$EVENT_LISTENERS: class $className not found")
    checkConfiguration(VarifyEventListener::class.java.isAssignableFrom(loadedClass)) {
        "Configuration parameter varify.$EVENT_LISTENERS: class $className is not a VarifyEventListener"
    }
    @Suppress("UNCHECKED_CAST")
    return (loadedClass as Class<out VarifyEventListener>).kotlin
}

private fun KClass<out VarifyEventListener>.instantiate(): VarifyEventListener =
    objectInstance
        ?: try {
            createInstance()
        } catch (_: IllegalArgumentException) {
            throw VarifyConfigurationException(
                "Event listener $qualifiedName must be an object or have a constructor without (required) parameters",
            )
        }

private fun Int.limitOrNull(name: String): Int? {
    checkConfiguration(this >= 0) { "@Varify($name = $this) must not be negative" }
    return takeIf { it > 0 }
}

private fun String.seedOrNull(): Long? =
    takeIf { it.isNotEmpty() }?.let { seed ->
        seed.toLongOrNull() ?: throw VarifyConfigurationException("@Varify($SEED = \"$seed\") must be a long value")
    }

private fun ExtensionContext.configurationLimit(name: String): Int? =
    getConfigurationParameter("varify.$name").orElse(null)?.let { value ->
        value.toIntOrNull()?.takeIf { it > 0 }
            ?: throw VarifyConfigurationException("Configuration parameter varify.$name=$value must be a positive int")
    }

private fun ExtensionContext.configurationSeed(): Long? =
    getConfigurationParameter("varify.$SEED").orElse(null)?.let { value ->
        value.toLongOrNull()
            ?: throw VarifyConfigurationException("Configuration parameter varify.$SEED=$value must be a long value")
    }
