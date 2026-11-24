package varify.generator.primitive

import varify.generator.Sample

class IntSample(override val value: Int, val shrinker: IntShrinker): Sample<Int>() {
    override fun shrink(): List<Sample<Int>> =
        shrinker.shrink(value).map { IntSample(it, shrinker) }
}
