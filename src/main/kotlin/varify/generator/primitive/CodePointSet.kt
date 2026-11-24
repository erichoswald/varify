package varify.generator.primitive

import varify.config.*

private val VALID_CODE_POINTS = Character.MIN_CODE_POINT..Character.MAX_CODE_POINT

/** A set of code points, stored as ranges in ascending order that neither overlap nor touch. */
internal class CodePointSet(ranges: Iterable<IntRange>) {
    val ranges: List<IntRange> = merge(ranges)

    init {
        checkConfiguration(this.ranges.all { it.first in VALID_CODE_POINTS && it.last in VALID_CODE_POINTS }) {
            "code points ${this.ranges} must be in $VALID_CODE_POINTS"
        }
    }

    private val offsets = this.ranges.runningFold(0) { offset, range -> offset + range.size() }.toIntArray()
    val count = offsets.last()

    operator fun get(position: Int): Int {
        // The range containing the position: either the one starting there, or, if none does, the last one starting
        // before (the insertion point minus one, as binarySearch returns -(insertion point) - 1).
        val index = offsets.binarySearch(position)
            .let { if (it >= 0) it else -it - 2 }
        return ranges[index].first + (position - offsets[index])
    }

    fun positionOf(codePoint: Int): Int? =
        ranges.indexOfFirst { codePoint in it }
            .takeIf { it >= 0 }
            ?.let { offsets[it] + (codePoint - ranges[it].first) }

    fun filter(predicate: (Int) -> Boolean): CodePointSet =
        CodePointSet(ranges.flatMap { range -> range.split(predicate) })

    operator fun plus(other: Iterable<IntRange>): CodePointSet =
        CodePointSet(ranges + other)

    private companion object {
        fun merge(ranges: Iterable<IntRange>): List<IntRange> =
            ranges
                .filterNot(IntRange::isEmpty)
                .sortedBy(IntRange::first)
                .fold(mutableListOf()) { merged, range ->
                    val last = merged.lastOrNull()
                    when {
                        last == null || last.last + 1 < range.first -> merged += range
                        last.last < range.last -> merged[merged.lastIndex] = last.first..range.last
                    }
                    merged
                }

        fun IntRange.size(): Int = last - first + 1

        fun IntRange.split(predicate: (Int) -> Boolean): List<IntRange> {
            val result = mutableListOf<IntRange>()
            var start: Int? = null
            for (codePoint in this) {
                if (predicate(codePoint)) {
                    if (start == null) start = codePoint
                } else if (start != null) {
                    result += start..<codePoint
                    start = null
                }
            }
            if (start != null) result += start..last
            return result
        }
    }
}
