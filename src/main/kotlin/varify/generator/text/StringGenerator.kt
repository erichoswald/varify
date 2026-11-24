package varify.generator.text

import varify.config.*
import varify.generator.*
import varify.generator.collection.*
import varify.generator.primitive.*

/**
 * Creates string generators. A string is a list of chars or code points, so it shrinks like a list: first to fewer of
 * them, then to smaller ones (towards 'a'). Lengths count chars or code points; a code point above `0xFFFF` takes two
 * chars.
 */
@Suppress("ClassName", "unused")
object string {
    // Mostly readable, but also any other code points, including those above 0xFFFF. Strings shrink towards ASCII.
    private val defaultCodePoints by lazy {
        mix(codePoint.ascii().printable(), codePoint.bmp().printable(), codePoint())
    }

    /** Generates strings of any code points (mostly printable ones) with lengths from 0 to [DEFAULT_MAX_SIZE]. */
    operator fun invoke(): Generator<String> =
        invoke(defaultCodePoints)

    /** Generates strings of any code points (mostly printable ones) with lengths from [length], e.g. `int(1..10)`. */
    fun ofLength(length: Generator<Int>): Generator<String> =
        invoke(defaultCodePoints, length)

    /** Generates strings of [chars], e.g. `string(char.ascii().letter())`. */
    @JvmName("ofChars")
    operator fun invoke(chars: Generator<Char>, length: Generator<Int> = defaultSizeGen): Generator<String> =
        MappedGenerator(list(chars, length)) { String(it.toCharArray()) }

    /** Generates strings of [codePoints], e.g. `string(codePoint().letter())`. */
    @JvmName("ofCodePoints")
    operator fun invoke(codePoints: Generator<Int>, length: Generator<Int> = defaultSizeGen): Generator<String> =
        MappedGenerator(list(codePoints, length)) { values ->
            buildString {
                for (value in values) {
                    checkConfiguration(Character.isValidCodePoint(value)) { "$value is not a valid code point" }
                    appendCodePoint(value)
                }
            }
        }
}

/**
 * Generates strings that join [count] [segments] like [joinToString], e.g. `join(string(char.hex()), separator = ".")`.
 *
 * This is a list mapped to a single value. Other values can be joined the same way, e.g. `list(int()).map { it.sum() }`.
 */
fun join(
    segments: Generator<String>,
    count: Generator<Int> = defaultSizeGen,
    separator: String = "",
    prefix: String = "",
    postfix: String = "",
): Generator<String> =
    MappedGenerator(list(segments, count), listOf(separator, prefix, postfix)) {
        it.joinToString(separator, prefix, postfix)
    }
