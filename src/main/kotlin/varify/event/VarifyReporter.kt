package varify.event

/**
 * The output channel for event listeners, passed with [VarificationStarted].
 * The format is compatible with that of JUnit's report entries.
 */
fun interface VarifyReporter {
    fun report(entries: Map<String, String>)

    companion object {
        /** Discards all entries. */
        val None = VarifyReporter { }
    }
}

fun VarifyReporter.report(key: String, value: Any) {
    report(mapOf(key to value.toString()))
}
