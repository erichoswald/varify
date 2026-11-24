package varify.generator.primitive

import varify.generator.*

private val SURROGATES = Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code

/**
 * Generates Unicode code points (as `Int` values) of a set, given as ranges, as described for [CodePointSetGenerator].
 *
 * Code points above `0xFFFF` (e.g. emoji) need two chars, a surrogate pair, in a string.
 */
@Suppress("unused")
class CodePointGenerator internal constructor(set: CodePointSet) : CodePointSetGenerator<Int, CodePointGenerator>(set) {
    constructor(ranges: Iterable<IntRange>) : this(CodePointSet(ranges))

    val ranges: List<IntRange> get() = set.ranges

    override val key = generatorKey<CodePointGenerator>(ranges)

    override fun valueOf(codePoint: Int): Int = codePoint

    override fun withSet(set: CodePointSet): CodePointGenerator = CodePointGenerator(set)

    /** Only the code points of this set that match [predicate]. */
    fun filter(predicate: (Int) -> Boolean): CodePointGenerator =
        filterCodePoints(predicate)

    /** Only the code points of this set that don't match [predicate]. */
    fun filterNot(predicate: (Int) -> Boolean): CodePointGenerator =
        filter { !predicate(it) }

    operator fun plus(other: CodePointGenerator): CodePointGenerator =
        CodePointGenerator(set + other.ranges)
    operator fun plus(range: IntRange): CodePointGenerator =
        this + CodePointGenerator(listOf(range))
    operator fun plus(chars: String): CodePointGenerator =
        this + codePoint(chars)

    override fun toString(): String =
        "CodePointGenerator($ranges)"
}

@Suppress("ClassName", "unused")
object codePoint {
    /** Generates all code points except surrogates, which are only valid in pairs. */
    operator fun invoke(): CodePointGenerator =
        CodePointGenerator(listOf(0..<SURROGATES.first, SURROGATES.last + 1..Character.MAX_CODE_POINT))

    /** Generates the code points in [ranges], e.g. `codePoint(0x1F600..0x1F64F)` for emoticons. */
    operator fun invoke(vararg ranges: IntRange): CodePointGenerator =
        CodePointGenerator(ranges.toList())

    /** Generates the code points of the characters in [chars], e.g. `codePoint("ä😀")`. */
    operator fun invoke(chars: String): CodePointGenerator =
        CodePointGenerator(chars.codePoints().toArray().map { it..it })

    /** Generates ASCII code points (`0x00` to `0x7F`), e.g. `codePoint.ascii().printable()`. */
    fun ascii(): CodePointGenerator = CodePointGenerator(listOf(0x00..0x7F))

    /** Generates ISO Latin-1 code points (`0x00` to `0xFF`). */
    fun latin1(): CodePointGenerator = CodePointGenerator(listOf(0x00..0xFF))

    /** Generates the code points of the Basic Multilingual Plane (`0x0000` to `0xFFFF`) except surrogates. */
    fun bmp(): CodePointGenerator = CodePointGenerator(listOf(0..<SURROGATES.first, SURROGATES.last + 1..0xFFFF))
}
