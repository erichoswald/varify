package varify.generator

abstract class Sample<out V> {
    abstract val value: V
    abstract fun shrink(): List<Sample<V>>

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sample<*>

        return value == other.value
    }

    override fun hashCode(): Int =
        value?.hashCode() ?: 0

    override fun toString(): String =
        "${javaClass.simpleName}(value=$value)"
}

class Unshrinkable<out V>(override val value: V) : Sample<V>() {
    override fun shrink(): List<Sample<V>> = emptyList()
    override fun toString(): String = "Unshrinkable($value)"
}
