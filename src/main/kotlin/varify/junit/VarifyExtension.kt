package varify.junit

import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.*
import org.opentest4j.*
import varify.*
import varify.config.*
import varify.event.*
import varify.syntax.*
import java.lang.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Runs test methods with a [VarifyContext] parameter (or context parameter) repeatedly within a single JUnit test.
 *
 * JUnit allows proceeding with a test method invocation only once, and its own repetition mechanisms report each
 * repetition as a separate test. This extension therefore skips JUnit's invocation and invokes the test method itself,
 * reusing the arguments JUnit resolved.
 *
 * When a test fails, the extension saves its seed in the directory `.varify/failed-seeds` (or the one given by the
 * configuration parameter `varify.failedSeedsDirectory`). The next execution of the test first reruns that seed. If the
 * rerun passes, the extension deletes the seed and continues with a random seed. Tests with a seed set by [Varify] or
 * `varify.seed` neither save nor rerun seeds, and the configuration parameter `varify.failedSeeds=off` disables both.
 *
 * Suspending test methods require kotlinx-coroutines-test on the test classpath.
 */
class VarifyExtension internal constructor(
    private val runSuspending: (suspend () -> Unit) -> Unit,
) : ParameterResolver, InvocationInterceptor {
    constructor() : this(::runInTestScope)

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        // Only test methods are varified, not constructors or lifecycle methods.
        parameterContext.parameter.type == VarifyContext::class.java &&
            parameterContext.declaringExecutable == extensionContext.testMethod.orElse(null)

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        DelegatingVarify()

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void?>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val contexts = invocationContext.arguments.filterIsInstance<DelegatingVarify>()
        if (contexts.isEmpty()) {
            // Non-varify test.
            invocation.proceed()
        } else {
            val configuration = extensionContext.varifyConfiguration()
            invocation.skip()
            varify(invocationContext, extensionContext, contexts, configuration)
        }
    }

    private fun varify(
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
        contexts: List<DelegatingVarify>,
        configuration: VarifyConfiguration,
    ) {
        val invokeTestMethod = testMethodInvoker(invocationContext)
        // Report entries of the current test if supported by test runner.
        val reporter = VarifyReporter { extensionContext.publishReportEntry(it) }
        fun varify(configuration: VarifyConfiguration) {
            try {
                varify(configuration, reporter, extensionContext.requiredTestClass) {
                    contexts.forEach { it.delegate = this }
                    invokeTestMethod()
                }
            } finally {
                contexts.forEach { it.delegate = null }
            }
        }

        val failedSeeds = extensionContext.failedSeeds()?.takeIf { extensionContext.configuredSeed() == null }
        val testId = extensionContext.uniqueId
        failedSeeds?.load(testId)?.let { seed ->
            reporter.report(SUMMARY_REPORT_KEY, "Varify: rerunning seed $seed of a previous failure")
            varify(configuration.copy(randomizer = Randomizer.System(seed)))

            // We only arrive here if the rerun didn't fail.
            failedSeeds.delete(testId)
        }
        try {
            varify(configuration)
        } catch (error: Throwable) {
            // Aborted tests and configuration errors don't depend on the seed.
            if (error !is TestAbortedException && error !is VarifyConfigurationException) {
                failedSeeds?.save(testId, configuration.randomizer.seed)
            }
            throw error
        }
    }

    private fun testMethodInvoker(invocationContext: ReflectiveInvocationContext<Method>): () -> Unit {
        val method = invocationContext.executable
        val target = invocationContext.target.orElse(null)
        val arguments = invocationContext.arguments.toTypedArray()

        val function = method.kotlinFunction
        if (function == null || !function.isSuspend) {
            return { ReflectionSupport.invokeMethod(method, target, *arguments) }
        }

        // JUnit's support for suspending test methods is internal, so we invoke them ourselves.
        function.isAccessible = true
        return {
            var started = false
            try {
                runSuspending {
                    started = true
                    function.callSuspend(target, *arguments)
                }
            } catch (error: LinkageError) {
                val throwable = error
                    .takeIf { started } // runSuspending was properly resolved, some other error occurred.
                    ?: VarifyConfigurationException(
                        "Suspending test method ${method.name} requires kotlinx-coroutines-test " +
                            "$MIN_COROUTINES_TEST_VERSION or later on the test classpath ($error)",
                    )
                throw throwable
            }
        }
    }
}
