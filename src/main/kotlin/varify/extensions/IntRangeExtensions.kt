package varify.extensions

// A Long because ranges can have more than `Int.MAX_VALUE` values.
fun IntRange.size(): Long =
    maxOf(last.toLong() - first + 1, 0)
