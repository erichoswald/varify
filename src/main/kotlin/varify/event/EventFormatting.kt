package varify.event

internal fun attempts(count: Int): String =
    count(count, "attempt")

internal fun count(count: Int, noun: String): String =
    when (count) {
        1 -> "1 $noun"
        else -> "$count ${noun}s"
    }

internal fun describe(error: Throwable): String {
    val name = error.javaClass.simpleName.ifEmpty { error.javaClass.name }
    val message = error.message?.lineSequence()?.first()
    return when {
        message.isNullOrEmpty() -> name
        else -> "$name: $message"
    }
}

internal fun render(value: Any?): String =
    when (value) {
        is String -> "\"${escape(value, '"')}\""
        is Char -> "'${escape(value.toString(), '\'')}'"
        else -> value.toString()
    }

private fun escape(text: String, quote: Char): String =
    buildString {
        for (char in text) {
            when {
                char == quote || char == '\\' -> append('\\').append(char)
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char.isISOControl() -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
    }
