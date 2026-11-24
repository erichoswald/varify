package varify.generator.primitive

import varify.config.Randomizer
import varify.config.checkConfiguration
import varify.generator.Generator
import varify.generator.Sample

// The preferred target of shrinking, more readable in failure reports than e.g. '\u0000'.
private const val PREFERRED_TARGET = 'a'.code

/**
 * Generates the values of a set of code points, given as ranges. Filters (e.g. [letter]) and unions derive other sets.
 *
 * After a few special values, values are generated in random order, each once.
 * Special values are the shrinking target and the first and last value of the set.
 * The shrinking target is 'a' if the set contains it, otherwise its first value.
 *
 * To control how often parts of a set are generated (e.g. mostly ASCII, sometimes other values), [varify.generator.mix] several
 * generators instead of combining their sets.
 */
@Suppress("unused")
abstract class CodePointSetGenerator<V, G : CodePointSetGenerator<V, G>> internal constructor(
    internal val set: CodePointSet,
) : Generator<V> {
    init {
        checkConfiguration(set.count > 0) { "set of ${this::class.simpleName} must not be empty" }
    }

    private val target = set.positionOf(PREFERRED_TARGET) ?: 0
    private val shrinker = BisectingIntShrinker(target)

    protected abstract fun valueOf(codePoint: Int): V

    internal abstract fun withSet(set: CodePointSet): G

    override fun generate(randomizer: Randomizer): Sequence<Sample<V>> {
        val specials = setOf(target, 0, set.count - 1)
        val others = randomizer.permutation(0L..<set.count.toLong())
            .map(Long::toInt)
            .filter { it !in specials }
        return (specials.asSequence() + others).map { PositionSample(it) }
    }

    internal fun filterCodePoints(predicate: (Int) -> Boolean): G =
        withSet(set.filter(predicate))

    fun letter(): G = filterCodePoints { Character.isLetter(it) }
    fun digit(): G = filterCodePoints { Character.isDigit(it) }
    fun letterOrDigit(): G = filterCodePoints { Character.isLetterOrDigit(it) }
    fun lowerCase(): G = filterCodePoints { Character.isLowerCase(it) }
    fun upperCase(): G = filterCodePoints { Character.isUpperCase(it) }
    fun whitespace(): G = filterCodePoints { Character.isWhitespace(it) }

    fun printable(): G =
        filterCodePoints { Character.getType(it) !in NON_PRINTABLE_TYPES }

    private inner class PositionSample(private val position: Int) : Sample<V>() {
        override val value: V = valueOf(set[position])

        override fun shrink(): List<Sample<V>> =
            shrinker.shrink(position).map { PositionSample(it) }
    }

    private companion object {
        val NON_PRINTABLE_TYPES = setOf(
            Character.CONTROL,
            Character.FORMAT,
            Character.SURROGATE,
            Character.PRIVATE_USE,
            Character.UNASSIGNED,
        ).map(Byte::toInt)
    }
}
