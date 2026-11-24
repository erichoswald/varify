package varify.generator.primitive

import varify.generator.*

/** Generates the chars of a set, given as ranges, as described for [CodePointSetGenerator]. */
@Suppress("unused")
class CharGenerator internal constructor(set: CodePointSet) : CodePointSetGenerator<Char, CharGenerator>(set) {
    constructor(ranges: Iterable<CharRange>) : this(CodePointSet(ranges.map { it.first.code..it.last.code }))

    val ranges: List<CharRange> get() = set.ranges.map { it.first.toChar()..it.last.toChar() }

    override val key = generatorKey<CharGenerator>(ranges)

    override fun valueOf(codePoint: Int): Char = codePoint.toChar()

    override fun withSet(set: CodePointSet): CharGenerator = CharGenerator(set)

    /** Only the chars of this set that match [predicate]. */
    fun filter(predicate: (Char) -> Boolean): CharGenerator =
        filterCodePoints { predicate(it.toChar()) }

    /** Only the chars of this set that don't match [predicate]. */
    fun filterNot(predicate: (Char) -> Boolean): CharGenerator =
        filter { !predicate(it) }

    operator fun plus(other: CharGenerator): CharGenerator =
        CharGenerator(set + other.set.ranges)
    operator fun plus(range: CharRange): CharGenerator =
        this + CharGenerator(listOf(range))
    operator fun plus(chars: String): CharGenerator =
        this + CharGenerator(chars.map { it..it })

    override fun toString(): String =
        "CharGenerator($ranges)"
}

@Suppress("ClassName", "unused")
object char {
    /** Generates all chars except surrogates, which only form characters in pairs. */
    operator fun invoke(): CharGenerator =
        CharGenerator(listOf(Char.MIN_VALUE..<Char.MIN_SURROGATE, Char.MAX_SURROGATE + 1..Char.MAX_VALUE))

    /** Generates the chars in [ranges], e.g. `char('a'..'z', '0'..'9')`. */
    operator fun invoke(vararg ranges: CharRange): CharGenerator =
        CharGenerator(ranges.toList())

    /** Generates the given [chars], e.g. `char("+-*")`. */
    operator fun invoke(chars: String): CharGenerator =
        CharGenerator(chars.map { it..it })

    /** Generates ASCII chars (`\u0000` to `\u007F`), e.g. `char.ascii().printable()`. */
    fun ascii(): CharGenerator = CharGenerator(listOf('\u0000'..'\u007F'))

    /** Generates ISO Latin-1 chars (`\u0000` to `\u00FF`). */
    fun latin1(): CharGenerator = CharGenerator(listOf('\u0000'..'\u00FF'))

    /** Generates hexadecimal digits, with lower case letters unless [upperCase]. */
    fun hex(upperCase: Boolean = false): CharGenerator =
        CharGenerator(listOf('0'..'9', if (upperCase) 'A'..'F' else 'a'..'f'))
}
