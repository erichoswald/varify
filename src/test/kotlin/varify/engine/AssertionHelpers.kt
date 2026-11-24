package varify.engine

// An assertion helper outside the test classes, like those of assertion libraries or shared test utilities.
object AssertionHelpers {
    fun assertSmall(value: Int) {
        if (value >= 300) throw AssertionError("too big: $value")
    }
}
