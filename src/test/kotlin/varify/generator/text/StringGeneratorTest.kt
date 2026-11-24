package varify.generator.text

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*
import varify.generator.collection.*
import varify.generator.primitive.*

class StringGeneratorTest : GeneratorContract<String>() {

    override fun subject(): Generator<String> =
        string(char('a'..'c'), int(0..3))

    @Test
    fun `The generator generates strings of its chars with lengths from its length generator`() {
        val values = string(char('x'..'z'), int(2..4)).allSampleValues()

        expectThat(values).all { get { length }.isIn(2..4) }
        expectThat(values.flatMap { it.toList() }.toSet()).isEqualTo(setOf('x', 'y', 'z'))
    }

    @Test
    fun `The generator generates strings of code points as surrogate pairs where needed`() {
        val values = string(codePoint(0x1F600..0x1F602), int(1..3)).allSampleValues()

        expectThat(values).all {
            get { codePointCount(0, length) }.isIn(1..3)
            get { length == 2 * codePointCount(0, length) }.isTrue()
        }
    }

    @Test
    fun `The default generator generates ASCII, other, and supplementary code points, all well-formed`() {
        val values = string().generate(Randomizer.System()).take(300).map { it.value }.toList()
        val codePoints = values.flatMap { it.codePoints().toArray().toList() }

        expectThat(values).contains("")
        expectThat(codePoints).none { isIn(0xD800..0xDFFF) } // No unpaired surrogates.
        expectThat(codePoints.count { it < 0x80 }).isGreaterThan(codePoints.size / 5)
        expectThat(codePoints.count { it in 0x80..0xFFFF }).isGreaterThan(0)
        expectThat(codePoints.count { it > 0xFFFF }).isGreaterThan(0)
        expectThat(values).all { get { codePointCount(0, length) }.isLessThanOrEqualTo(DEFAULT_MAX_SIZE) }
    }

    @Test
    fun `The default generator takes another length generator`() {
        expectThat(string.ofLength(int(3..3)).generate(Randomizer.System()).take(100).toList())
            .all { get { value.codePointCount(0, value.length) }.isEqualTo(3) }
    }

    @Test
    fun `The generator rejects values that are not code points as a configuration error`() {
        expectThrows<VarifyConfigurationException> { string(int(-5..-1), int(1..1)).allSampleValues() }
            .get { message }.isEqualTo("-1 is not a valid code point")
    }

    @Test
    fun `The generated samples shrink to shorter strings, then to strings of smaller chars`() {
        // The shrinking target of 'b'..'z' is 'b'.
        val sample = string(char('b'..'z'), int(0..2)).allSamples().first { it.value.length == 2 }

        expectThat(sample.value.toSet()).isEqualTo(setOf('b', 'z'))
        expectThat(sample.shrink().map { it.value }).contains("", "b", "z", "bb")
    }

    @Test
    fun `The join generator joins segments with a separator, prefix, and postfix`() {
        val values = join(string(char('a'..'b'), int(1..2)), int(2..3), separator = ".", prefix = "<", postfix = ">")
            .allSampleValues()

        expectThat(values).isNotEmpty().all { matches(Regex("<[ab]{1,2}(\\.[ab]{1,2}){1,2}>")) }
    }

    @Test
    fun `Join generators with different separators have different keys`() {
        val segments = string(char('a'..'b'))

        expectThat(join(segments, separator = ",").key).isNotEqualTo(join(segments, separator = ";").key)
        expectThat(join(segments, separator = ",").key).isEqualTo(join(segments, separator = ",").key)
    }
}
