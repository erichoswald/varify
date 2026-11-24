package varify.generator

import varify.config.Randomizer

data class NullableGenerator<T : Any>(private val source: Generator<T>) : Generator<T?> {
    override val key = generatorKey<NullableGenerator<*>>(source.key)

    override fun generate(randomizer: Randomizer): Sequence<Sample<T?>> =
        sequenceOf(Unshrinkable(null)) + source.generate(randomizer)

    override fun rebuild(sample: Sample<*>): Sample<T?> =
        if (sample.value == null) Unshrinkable(null) else source.rebuild(sample)
}

fun <T : Any> Generator<T>.orNull(): NullableGenerator<T> =
    NullableGenerator(this)
