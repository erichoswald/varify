package varify.generator.primitive

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.*

class CodePointGeneratorTest : GeneratorContract<Int>() {

    override fun subject(): CodePointGenerator =
        codePoint(0x1F600..0x1F602, 'a'.code..'c'.code)

    @Test
    fun `The generator generates each code point of its set once, 'a' first`() {
        val values = subject().allSampleValues()

        expectThat(values.first()).isEqualTo('a'.code)
        expectThat(values.toSet()).isEqualTo(('a'.code..'c'.code) union (0x1F600..0x1F602))
        expectThat(values).hasSize(6)
    }

    @Test
    fun `The generator without ranges generates all code points except surrogates`() {
        expectThat(codePoint().ranges).containsExactly(0x0000..0xD7FF, 0xE000..0x10FFFF)
        expectThat(codePoint.bmp().ranges).containsExactly(0x0000..0xD7FF, 0xE000..0xFFFF)
    }

    @Test
    fun `The generator generates code points beyond the Basic Multilingual Plane`() {
        val values = codePoint().generate(Randomizer.System()).take(1000).map { it.value }.toList()

        expectThat(values.count { it > 0xFFFF }).isGreaterThan(800)
        expectThat(values).none { isIn(0xD800..0xDFFF) }
    }

    @Test
    fun `The generator takes the code points of a string, including surrogate pairs`() {
        expectThat(codePoint("aä😀").ranges).containsExactly('a'.code..'a'.code, 'ä'.code..'ä'.code, 0x1F600..0x1F600)
    }

    @Test
    fun `The generator filters code points beyond the Basic Multilingual Plane by their properties`() {
        val letters = codePoint(0x10400..0x1044F).letter() // Deseret, a script outside the Basic Multilingual Plane.

        expectThat(letters.ranges).containsExactly(0x10400..0x1044F)
        expectThat(codePoint(0x1F600..0x1F64F).printable().ranges).containsExactly(0x1F600..0x1F64F) // Emoticons.
    }

    @Test
    fun `The generator rejects values that are not code points as a configuration error`() {
        expectThrows<VarifyConfigurationException> { codePoint(0..0x110000) }
        expectThrows<VarifyConfigurationException> { codePoint(-1..10) }
    }
}
