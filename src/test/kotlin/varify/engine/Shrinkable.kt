package varify.engine

import varify.config.*
import varify.generator.*

data class Shrinkable(private val value: Int) : Generator<Int> {
    override val key = generatorKey<Shrinkable>(value)

    override fun generate(randomizer: Randomizer): Sequence<Sample<Int>> =
        sequenceOf(ShrinkableSample(value))
}

class ShrinkableSample(override val value: Int) : Sample<Int>() {
    override fun shrink(): List<Sample<Int>> =
        when {
            value > 0 -> listOf(ShrinkableSample(value - 1))
            else -> emptyList()
        }
}
