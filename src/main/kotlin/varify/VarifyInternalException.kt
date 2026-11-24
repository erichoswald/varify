package varify

/**
 * Indicates a failed assumption in Varify itself.
 * It is not a test failure, so Varify aborts immediately instead of shrinking.
 */
class VarifyInternalException(message: String) :
    IllegalStateException("$message (this is a bug in Varify, please report it)")

internal inline fun checkInternal(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        throw VarifyInternalException(lazyMessage())
    }
}

internal inline fun <T : Any> checkInternalNotNull(value: T?, lazyMessage: () -> String): T =
    value ?: throw VarifyInternalException(lazyMessage())
