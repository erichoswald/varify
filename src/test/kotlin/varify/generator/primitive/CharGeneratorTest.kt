package varify.generator.primitive

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*

class CharGeneratorTest : GeneratorContract<Char>() {

    override fun subject(): CharGenerator =
        char('x'..'z', 'a'..'e')

    @Test
    fun `The generator generates each char of its set once`() {
        val values = subject().allSampleValues()

        expectThat(values).hasSize(8)
        expectThat(values.toSet()).isEqualTo(('a'..'e') union ('x'..'z'))
    }

    @Test
    fun `The generator generates 'a' first, then the first and the last char of its set`() {
        expectThat(char('0'..'9', 'a'..'f').allSampleValues().take(3)).containsExactly('a', '0', 'f')
    }

    @Test
    fun `The generator merges overlapping and adjacent ranges`() {
        expectThat(char('c'..'f', 'a'..'d', 'g'..'h', 'x'..'x').ranges).containsExactly('a'..'h', 'x'..'x')
    }

    @Test
    fun `The generator without ranges generates all chars except surrogates`() {
        val ranges = char().ranges

        expectThat(ranges).containsExactly('\u0000'..'\uD7FF', '\uE000'..'\uFFFF')
    }

    @Test
    fun `The generator filters its set by predicates`() {
        expectThat(char.ascii().letter().allSampleValues().toSet()).isEqualTo(('a'..'z') union ('A'..'Z'))
        expectThat(char.ascii().digit().ranges).containsExactly('0'..'9')
        expectThat(char.ascii().printable().ranges).containsExactly(' '..'~')
        expectThat(char.ascii().filterNot(Char::isLetterOrDigit).printable().allSampleValues()).hasSize(95 - 62)
    }

    @Test
    fun `The generator extends its set by unions`() {
        val generator = char.ascii().letterOrDigit() + "_-" + ('à'..'å') + char("!")

        expectThat(generator.allSampleValues()).hasSize(62 + 2 + 6 + 1)
    }

    @Test
    fun `The hex generator generates lower or upper case hexadecimal digits`() {
        expectThat(char.hex().allSampleValues().toSet()).isEqualTo("0123456789abcdef".toSet())
        expectThat(char.hex(upperCase = true).allSampleValues().toSet()).isEqualTo("0123456789ABCDEF".toSet())
    }

    @Test
    fun `The generated samples shrink towards 'a' within the set`() {
        val sample = char('0'..'9', 'a'..'z').allSamples().first { it.value == 'z' }

        expectThat(sample.shrink().map { it.value }).containsExactly('a', 'n', 't', 'w', 'y')
    }

    @Test
    fun `The generated samples shrink towards the first char of a set without 'a'`() {
        val sample = char('0'..'9', 'A'..'Z').allSamples().first { it.value == 'B' }

        expectThat(sample.shrink().map { it.value }.first()).isEqualTo('0')
        expectThat(sample.shrink().map { it.value }.all { it in '0'..'9' || it in 'A'..'Z' }).isTrue()
    }

    @Test
    fun `The generator rejects an empty set as a configuration error`() {
        expectThrows<VarifyConfigurationException> { char.ascii().filter { false } }
            .get { message }.isEqualTo("set of CharGenerator must not be empty")
    }

    @Test
    fun `The generator of a char range is a char generator instead of a list of the range`() {
        expectThat(from('a'..'z')).isA<CharGenerator>().get { ranges }.containsExactly('a'..'z')
    }

    @Test
    fun `The generator works with the full set of chars`() {
        val values = char().generate(Randomizer.System()).take(1000).map { it.value }.toList()

        expectThat(values.toSet()).hasSize(1000)
        expectThat(values).none { isIn(Char.MIN_SURROGATE..Char.MAX_SURROGATE) }
    }
}
