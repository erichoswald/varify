package varify.generator

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.generator.primitive.*
import varify.syntax.*

class MappedGeneratorTest : GeneratorContract<String>() {

    override fun subject(): Generator<String> =
        int(0..5).map { "#$it" }

    @Test
    fun `The generator transforms all source samples in their order`() {
        val values = OrderedGenerator(1, 2, 3).map { it * 2 }.allSampleValues()

        expectThat(values).containsExactly(2, 4, 6)
    }

    @Test
    fun `The generated samples shrink through their source samples`() {
        val source = int(0..100)
        val sample = MappedSample(IntSample(80, source.shrinker)) { "#$it" }

        expectThat(sample.shrink().map { it.value }).containsExactly("#0", "#40", "#60", "#70", "#75", "#78", "#79")
    }

    @Test
    fun `The generator has the same key for lambdas from the same call site with different captured values`() {
        fun offset(by: Int) = int(0..5).map { it + by }

        expectThat(offset(1).key).isEqualTo(offset(2).key)
    }

    @Test
    fun `The generator has different keys for lambdas from different call sites`() {
        expectThat(int(0..5).map { it + 1 }.key).isNotEqualTo(int(0..5).map { it + 1 }.key)
    }

    @Test
    fun `The generator rebuilds a sample with its own transformation and the same source sample`() {
        fun offset(by: Int) = int(0..5).map { it + by }
        val sample = offset(1).allSamples().first { it.value == 6 }

        val rebuilt = offset(10).rebuild(sample)

        expectThat(rebuilt.value).isEqualTo(15)
        expectThat(rebuilt.shrink().map { it.value }).containsExactly(10, 13, 14)
    }

    @Test
    fun `The generator can capture earlier samples of the test function`() {
        val pairs = mutableListOf<Pair<Int, Int>>()

        varify(VarifyConfiguration(randomizer = TestRandomizer())) {
            val first = some(int(0..2))
            pairs += first to some(int(0..1).map { first + it })
        }

        expectThat(pairs).containsExactlyInAnyOrder(0 to 0, 0 to 1, 1 to 1, 1 to 2, 2 to 2, 2 to 3)
    }
}
