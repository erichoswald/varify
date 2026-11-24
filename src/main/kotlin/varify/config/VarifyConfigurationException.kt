package varify.config

/** Indicates that Varify is used or configured incorrectly. */
class VarifyConfigurationException(message: String) : IllegalArgumentException(message)

inline fun checkConfiguration(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        throw VarifyConfigurationException(lazyMessage())
    }
}
