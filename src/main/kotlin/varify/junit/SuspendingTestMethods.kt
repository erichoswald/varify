package varify.junit

import kotlinx.coroutines.test.*

/** The minimum version of kotlinx-coroutines-test for suspending test methods (`runTest` with a `Duration` timeout). */
internal const val MIN_COROUTINES_TEST_VERSION = "1.7.0"

/**
 * Runs [body] with `runTest` of kotlinx-coroutines-test.
 *
 * Varify only declares kotlinx-coroutines-test as a compile-time dependency, so only this file refers to it, and it's only
 * loaded for suspending test methods.
 */
internal fun runInTestScope(body: suspend () -> Unit) {
    runTest { body() }
}
