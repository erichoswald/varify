package varify.junit

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.*
import org.junit.platform.engine.*
import org.junit.platform.engine.reporting.*
import org.opentest4j.AssertionFailedError
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.engine.*
import varify.event.*
import varify.generator.primitive.*
import varify.syntax.*
import kotlin.time.Duration.Companion.days

class VarifyExtensionTest {

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class PassingFixture {
        @Test
        fun passes(varify: VarifyContext) {
            contexts += varify
            context(varify) {
                values += oneOf(1, 2, 3)
            }
        }

        companion object {
            val contexts = mutableListOf<VarifyContext>()
            val values = mutableListOf<Int>()
        }
    }

    @Test
    fun `A passing test method is invoked for each variation but reported as a single successful test`() {
        val events = executeFixture(PassingFixture::class)

        events.assertStatistics { it.started(1).succeeded(1) }
        expectThat(events.succeeded().list())
            .single()
            .get { testDescriptor.displayName }
            .isEqualTo("passes(VarifyContext)")
        expectThat(PassingFixture.values).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `All invocations of a test method receive the same context`() {
        executeFixture(PassingFixture::class)

        expectThat(PassingFixture.contexts).hasSize(3)
        expectThat(PassingFixture.contexts.toSet()).hasSize(1)
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class ContextParameterFixture {
        @Test
        context(_: VarifyContext)
        fun passes() {
            values += oneOf(1, 2, 3)
        }

        companion object {
            val values = mutableListOf<Int>()
        }
    }

    @Test
    fun `A test method with a context parameter is varified`() {
        executeFixture(ContextParameterFixture::class).assertStatistics { it.started(1).succeeded(1) }
        expectThat(ContextParameterFixture.values).containsExactlyInAnyOrder(1, 2, 3)
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class FailingFixture {
        @Test
        context(_: VarifyContext)
        fun fails() {
            invocations++
            val value = some(Shrinkable(5))
            if (value >= 2) throw AssertionError("too big: $value")
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `A failing test method is shrunk and reported as a single failed test with a report`() {
        val events = executeFixture(FailingFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<AssertionError>().and {
            get { message }.isEqualTo("too big: 2")
            get { suppressed.toList() }.single().isA<VarifyReport>()
        }
        // One variation attempt, then shrinking to 4, 3, 2 (failing) and 1 (passing).
        expectThat(FailingFixture.invocations).isEqualTo(5)
    }

    @TestFixture
    @Varify
    class TwoAssertionsFixture {
        @Test
        context(_: VarifyContext)
        fun fails() {
            val value = some(IntGenerator(896..896, shrinker = BisectingIntShrinker(0)))
            if (value < 10) throw AssertionError("too small: $value")
            if (value >= 300) throw AssertionError("too big: $value")
        }
    }

    @Test
    fun `A failing test method is not shrunk to a sample that fails a different assertion`() {
        val events = executeFixture(TwoAssertionsFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<AssertionError>()
            .get { message }.isEqualTo("too big: 300")
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class AbortingFixture {
        @Test
        context(_: VarifyContext)
        fun aborts() {
            invocations++
            some(Shrinkable(5))
            assumeTrue(false, "assumption")
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `A test method whose assumption fails is reported as aborted`() {
        executeFixture(AbortingFixture::class).assertStatistics { it.started(1).aborted(1) }
        expectThat(AbortingFixture.invocations).isEqualTo(1)
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class PlainFixture {
        @Test
        fun plain() {
            invocations++
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `A test method without context is invoked once by JUnit`() {
        executeFixture(PlainFixture::class).assertStatistics { it.started(1).succeeded(1) }
        expectThat(PlainFixture.invocations).isEqualTo(1)
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class LifecycleMethodFixture {
        @BeforeEach
        fun setUp(
            @Suppress("unused") varify: VarifyContext,
        ) {
        }

        @Test
        fun test() {
        }
    }

    @Test
    fun `A lifecycle method cannot receive a context`() {
        val events = executeFixture(LifecycleMethodFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<ParameterResolutionException>()
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class SuspendingFixture {
        @Test
        context(_: VarifyContext)
        suspend fun passes() {
            // Only completes quickly with the virtual time of runTest.
            delay(1.days)
            values += oneOf(1, 2, 3)
        }

        companion object {
            val values = mutableListOf<Int>()
        }
    }

    @Test
    @Timeout(10)
    fun `A suspending test method is varified with virtual time`() {
        executeFixture(SuspendingFixture::class).assertStatistics { it.started(1).succeeded(1) }
        expectThat(SuspendingFixture.values).containsExactlyInAnyOrder(1, 2, 3)
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class SuspendingParameterFixture {
        @Test
        suspend fun passes(varify: VarifyContext) {
            yield()
            context(varify) {
                values += oneOf(1, 2, 3)
            }
        }

        companion object {
            val values = mutableListOf<Int>()
        }
    }

    @Test
    fun `A suspending test method with a regular parameter is varified`() {
        executeFixture(SuspendingParameterFixture::class).assertStatistics { it.started(1).succeeded(1) }
        expectThat(SuspendingParameterFixture.values).containsExactlyInAnyOrder(1, 2, 3)
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    class SuspendingFailingFixture {
        @Test
        context(_: VarifyContext)
        suspend fun fails() {
            invocations++
            val value = some(Shrinkable(5))
            yield()
            if (value >= 2) throw AssertionError("too big: $value")
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `A failing suspending test method is shrunk`() {
        val events = executeFixture(SuspendingFailingFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<AssertionError>().and {
            get { message }.isEqualTo("too big: 2")
            get { suppressed.toList() }.filterIsInstance<VarifyReport>().hasSize(1)
        }
        // One variation attempt, then shrinking to 4, 3, 2 (failing) and 1 (passing).
        expectThat(SuspendingFailingFixture.invocations).isEqualTo(5)
    }

    @TestFixture
    @Suppress("RedundantSuspendModifier")
    class MissingCoroutinesTestFixture {
        @JvmField
        @RegisterExtension
        val varify = VarifyExtension { throw NoClassDefFoundError("kotlinx/coroutines/test/TestBuildersKt") }

        @Test
        context(_: VarifyContext)
        suspend fun passes() {
            oneOf(1, 2, 3)
        }
    }

    @Test
    fun `A suspending test method without kotlinx-coroutines-test fails with a configuration error`() {
        val events = executeFixture(MissingCoroutinesTestFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<VarifyConfigurationException>()
            .get { message }.isNotNull().startsWith(
                "Suspending test method passes requires kotlinx-coroutines-test 1.7.0 or later on the test classpath",
            )
    }

    @TestFixture
    @ExtendWith(VarifyExtension::class)
    @Suppress("RedundantSuspendModifier")
    class LinkageErrorFixture {
        @Test
        context(_: VarifyContext)
        suspend fun fails() {
            oneOf(1, 2, 3)
            throw NoClassDefFoundError("missing/Class")
        }
    }

    @Test
    fun `A linkage error of a suspending test method itself is not a configuration error`() {
        val events = executeFixture(LinkageErrorFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<NoClassDefFoundError>()
    }

    class AttemptsReportingListener : VarifyEventListener {
        private var reporter = VarifyReporter.None

        override fun onEvent(event: VarifyEvent) {
            when (event) {
                is VarificationStarted -> reporter = event.reporter
                is VariationsExhausted -> reporter.report("attempts", event.attempts)
                else -> Unit
            }
        }
    }

    @TestFixture
    class ReportingFixture {
        @Test
        @Varify(seed = "42", eventListeners = [AttemptsReportingListener::class])
        context(_: VarifyContext)
        fun passes() {
            oneOf(1, 2, 3)
        }
    }

    @Test
    fun `Event listeners and the success summary report as report entries of the test`() {
        val events = executeFixture(ReportingFixture::class)

        events.assertStatistics { it.started(1).succeeded(1).reportingEntryPublished(2) }
        expectThat(events.reportingEntryPublished().list()).all {
            get { testDescriptor.displayName }.isEqualTo("passes(VarifyContext)")
        }
        expectThat(events.reportingEntryPublished().list().map { it.getRequiredPayload(ReportEntry::class.java).keyValuePairs })
            .containsExactly(
                mapOf("attempts" to "3"),
                mapOf(SUMMARY_REPORT_KEY to "Varify: passed 3 attempts, all variations tried\n  seed: 42"),
            )
    }

    @TestFixture
    class CoverageFixture {
        @Test
        @Varify(seed = "42")
        context(_: VarifyContext)
        fun collects() {
            collect("value", oneOf(1, 2))
        }

        @Test
        @Varify(seed = "42")
        context(_: VarifyContext)
        fun missesCoverage() {
            cover("one", oneOf(1, 2) == 1, minPercent = 60)
        }
    }

    @Test
    fun `Collected values are reported in the summary, and missed coverage fails the test`() {
        val events = executeFixture(CoverageFixture::class)

        events.assertStatistics { it.started(2).succeeded(1).failed(1) }
        val reportEntries = events.reportingEntryPublished()
            .list()
            .map { it.getRequiredPayload(ReportEntry::class.java).keyValuePairs }
        expectThat(reportEntries).containsExactly(
            mapOf(
                SUMMARY_REPORT_KEY to
                    "Varify: passed 2 attempts, all variations tried\n  seed: 42\n  collected value: 2 (50%), 1 (50%)",
            ),
        )
        expectThat(events.failed().list().single().getRequiredPayload(TestExecutionResult::class.java).throwable.get())
            .isA<AssertionFailedError>()
            .get { message }
            .isEqualTo("Coverage of one is 50% (1 of 2 calls), required 60%")
    }

    @BeforeEach
    fun resetFixtures() {
        SuspendingFixture.values.clear()
        SuspendingParameterFixture.values.clear()
        SuspendingFailingFixture.invocations = 0
        PassingFixture.contexts.clear()
        PassingFixture.values.clear()
        ContextParameterFixture.values.clear()
        FailingFixture.invocations = 0
        AbortingFixture.invocations = 0
        PlainFixture.invocations = 0
    }
}
