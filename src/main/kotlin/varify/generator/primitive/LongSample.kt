package varify.generator.primitive

import varify.generator.Sample

class LongSample(override val value: Long, val shrinker: LongShrinker) : Sample<Long>() {
    override fun shrink(): List<Sample<Long>> =
        shrinker.shrink(value).map { LongSample(it, shrinker) }
}
