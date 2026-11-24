package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.config.*
import varify.generator.primitive.*

class ComposedGeneratorTest : GeneratorContract<String>() {

    override fun subject(): Generator<String> =
        compose(int(0..3), from("a", "b")) { n, s -> "$s$n" }

    private val digits = OrderedGenerator(0, 1, 2)

    @Test
    fun `The generator alternates diagonal combinations with random ones and yields each combination once`() {
        val values = compose(digits, digits) { a, b -> "$a$b" }.allSampleValues()

        // Diagonal: 00, 01, 10, 02, 11, 20, 12, 21, 22. Random (TestRandomizer): 01, 20, 12, then only repetitions.
        expectThat(values).containsExactly("00", "01", "20", "10", "12", "02", "11", "21", "22")
    }

    @Test
    fun `The generator combines all samples of finite sources with different lengths`() {
        val values = compose(from(0, 1), digits) { a, b -> "$a$b" }.allSampleValues()

        expectThat(values).containsExactlyInAnyOrder("00", "01", "02", "10", "11", "12")
    }

    @Test
    fun `The generator combines all samples of more sources`() {
        val values = compose(digits, digits, digits) { a, b, c -> "$a$b$c" }.allSampleValues()

        expectThat(values.first()).isEqualTo("000")
        expectThat(values).hasSize(27)
        expectThat(values.toSet()).hasSize(27)
    }

    @Test
    fun `The generator combines all five sources`() {
        val values = compose(digits, digits, digits, digits, digits) { a, b, c, d, e -> "$a$b$c$d$e" }.allSampleValues()

        expectThat(values.first()).isEqualTo("00000")
        expectThat(values).hasSize(243)
        expectThat(values.toSet()).hasSize(243)
    }

    @Test
    fun `The generator reaches later samples of all sources early`() {
        val positions = OrderedGenerator((0..<1000).toList()) // Each value is its position.

        val values = compose(positions, positions, positions, positions, positions) { a, b, c, d, e -> listOf(a, b, c, d, e) }
            .generate(TestRandomizer())
            .take(100)
            .map { it.value }
            .toList()

        // The diagonal alone would only use the first five samples of each source.
        expectThat((0..<5).map { source -> values.maxOf { it[source] } }).all { isGreaterThan(20) }
    }

    @Test
    fun `The generator only generates the source samples it needs from infinite sources`() {
        val values = compose(int(), OrderedGenerator(false, true)) { n, b -> n to b }.generate(TestRandomizer()).take(4).toList()

        expectThat(values.map { it.value }).containsExactly(0 to false, 0 to true, -1 to true, 1 to false)
    }

    @Test
    fun `The generator rebuilds a sample with its own function and the same source samples`() {
        fun offset(by: Int) = compose(digits, compose(digits, digits) { a, b -> a * b + by }) { a, b -> "$a:$b" }
        val sample = offset(1).allSamples().first { it.value == "2:5" }

        expectThat(offset(10).rebuild(sample).value).isEqualTo("2:14")
    }

    @Test
    fun `The pair generator combines two sources into pairs`() {
        val values = pair(from(0, 1), boolean).allSampleValues()

        expectThat(values).containsExactlyInAnyOrder(0 to false, 0 to true, 1 to false, 1 to true)
        expectThat(pair(digits, boolean).key).isEqualTo(pair(digits, boolean).key)
    }

    @Test
    fun `The generator generates nothing when a source generates nothing`() {
        val values = compose(digits, from<Int>()) { a, b -> a + b }.allSampleValues()

        expectThat(values).isEmpty()
    }

    @Test
    fun `The generated samples shrink one source at a time`() {
        val sample = compose(int(0..4), int(0..2)) { a, b -> a to b }
            .allSamples()
            .first { it.value == 4 to 2 }

        expectThat(sample.shrink().map { it.value }).containsExactly(0 to 2, 2 to 2, 3 to 2, 4 to 0, 4 to 1)
    }

    @Test
    fun `The generated samples don't shrink to combinations with the same value`() {
        val samples = compose(int(0..4), int(0..2)) { a, _ -> a }.allSamples()

        expectThat(samples).all { get { shrink().map { it.value } }.doesNotContain(subject.value) }
    }

    @Test
    fun `The generator has the same key for lambdas from the same call site with different captured values`() {
        fun offset(by: Int) = compose(digits, digits) { a, b -> a + b + by }

        expectThat(offset(1).key).isEqualTo(offset(2).key)
    }

    @Test
    fun `The generator has different keys for lambdas from different call sites`() {
        expectThat(compose(digits, digits) { a, b -> a + b }.key).isNotEqualTo(compose(digits, digits) { a, b -> a + b }.key)
    }
}
