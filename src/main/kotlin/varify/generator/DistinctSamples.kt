package varify.generator

/** The number of successive duplicates after which a sequence counts as exhausted, see [distinctUntilExhausted]. */
const val SUCCESSIVE_DUPLICATES_LIMIT = 1000

/** The distinct elements of this sequence, ending after [SUCCESSIVE_DUPLICATES_LIMIT] successive duplicates. */
internal fun <T> Sequence<T>.distinctUntilExhausted(): Sequence<T> =
    sequence {
        val seen = mutableSetOf<T>()
        var duplicates = 0
        for (element in this@distinctUntilExhausted) {
            if (seen.add(element)) {
                duplicates = 0
                yield(element)
            } else if (++duplicates >= SUCCESSIVE_DUPLICATES_LIMIT) {
                break
            }
        }
    }
