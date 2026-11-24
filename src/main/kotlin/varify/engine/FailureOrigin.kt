package varify.engine

/**
 * Identifies a failure's type and location in the test code.
 *
 * This enables shrinking to only accept failing samples that share the same failure origin as the original sample.
 * The location used is the first stack frame that belongs to the class of the actual test code. Stack frames in
 * assertion libraries or library functions are ignored. This means that the same type of exception thrown in a
 * different place in the called code is still considered the same failure.
 */
internal class FailureOrigin private constructor(
    private val errorClass: Class<*>,
    private val testFrame: TestFrame?,
) {
    private data class TestFrame(val className: String, val methodName: String, val lineNumber: Int)

    /** If a failure has no test frame (e.g. no stack trace), only the exception classes are compared. */
    fun matches(other: FailureOrigin): Boolean =
        errorClass == other.errorClass &&
            (testFrame == null || other.testFrame == null || testFrame == other.testFrame)

    companion object {
        fun of(error: Throwable, testClass: Class<*>): FailureOrigin {
            // Nested classes, lambdas, and continuations of the test class share its outermost class name as prefix.
            val outermostClassName = testClass.name.substringBefore('$')
            val testFrame = error.stackTrace
                .firstOrNull { it.className == outermostClassName || it.className.startsWith("$outermostClassName$") }
                ?.let { TestFrame(it.className, it.methodName, it.lineNumber) }
            return FailureOrigin(error.javaClass, testFrame)
        }
    }
}
