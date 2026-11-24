package varify.generator

import kotlin.reflect.*

/**
 * Identifies equivalent generators.
 *
 * A key consists of the generator type and the properties that determine its samples, including the keys of source
 * generators. Its string representation describes the generator, e.g. `IntGenerator(0..5, ...)`.
 */
data class GeneratorKey(
    val type: KClass<*>,
    val properties: List<Any?> = emptyList(),
) {
    override fun toString(): String {
        val name = type.simpleName ?: type.java.name
        return if (properties.isEmpty()) name else properties.joinToString(prefix = "$name(", postfix = ")")
    }
}
