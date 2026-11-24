package varify.junit

import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.*
import org.junit.platform.engine.reporting.*
import org.junit.platform.testkit.engine.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.engine.*
import varify.event.*
import varify.syntax.*
import java.nio.file.*

class FailedSeedsTest {

    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun reset() {
        SeedRecorder.seeds.clear()
        FailingFixture.failing = true
    }

    object SeedRecorder : VarifyEventListener {
        val seeds = mutableListOf<Long>()

        override fun onEvent(event: VarifyEvent) {
            if (event is VarificationStarted) seeds += event.configuration.randomizer.seed
        }
    }

    @TestFixture
    @Varify(eventListeners = [SeedRecorder::class])
    class FailingFixture {
        @Test
        context(_: VarifyContext)
        fun test() {
            some(Shrinkable(5))
            if (failing) throw AssertionError("fails")
        }

        companion object {
            var failing = true
        }
    }

    @TestFixture
    @Varify(seed = "42", eventListeners = [SeedRecorder::class])
    class SeededFixture {
        @Test
        context(_: VarifyContext)
        fun test() {
            some(Shrinkable(5))
            throw AssertionError("fails")
        }
    }

    @TestFixture
    @Varify(eventListeners = [SeedRecorder::class])
    class AbortedFixture {
        @Test
        context(_: VarifyContext)
        fun test() {
            Assumptions.assumeTrue(some(Shrinkable(5)) < 0)
        }
    }

    private fun execute(fixture: kotlin.reflect.KClass<*>, mode: String = "rerun"): Events =
        executeFixture(
            fixture,
            mapOf(
                "varify.failedSeeds" to mode,
                "varify.failedSeedsDirectory" to directory.toString(),
            ),
        )

    private fun Events.testId(): String =
        finished().list().single().testDescriptor.uniqueId.toString()

    private fun Events.reports(): List<String> =
        reportingEntryPublished().list().mapNotNull {
            it.getRequiredPayload(ReportEntry::class.java).keyValuePairs[SUMMARY_REPORT_KEY]
        }

    @Test
    fun `A failing test saves its seed`() {
        val events = execute(FailingFixture::class)

        events.assertStatistics { it.failed(1) }
        expectThat(FailedSeeds(directory).load(events.testId())).isEqualTo(SeedRecorder.seeds.single())
    }

    @Test
    fun `A test with a saved seed reruns it first and keeps it when it fails again`() {
        execute(FailingFixture::class)
        val seed = SeedRecorder.seeds.single()
        SeedRecorder.seeds.clear()

        val events = execute(FailingFixture::class)

        events.assertStatistics { it.failed(1) }
        expectThat(SeedRecorder.seeds).containsExactly(seed)
        expectThat(FailedSeeds(directory).load(events.testId())).isEqualTo(seed)
    }

    @Test
    fun `A test whose rerun passes deletes the saved seed and continues with a random seed`() {
        execute(FailingFixture::class)
        val seed = SeedRecorder.seeds.single()
        SeedRecorder.seeds.clear()
        FailingFixture.failing = false

        val events = execute(FailingFixture::class)

        events.assertStatistics { it.succeeded(1) }
        expectThat(SeedRecorder.seeds).hasSize(2).first().isEqualTo(seed)
        expectThat(events.reports().first()).isEqualTo("Varify: rerunning seed $seed of a previous failure")
        expectThat(FailedSeeds(directory).load(events.testId())).isNull()
    }

    @Test
    fun `A test with a configured seed neither saves nor reruns seeds`() {
        val first = execute(SeededFixture::class)
        expectThat(FailedSeeds(directory).load(first.testId())).isNull()

        FailedSeeds(directory).save(first.testId(), 7)
        SeedRecorder.seeds.clear()
        execute(SeededFixture::class)

        expectThat(SeedRecorder.seeds).containsExactly(42)
    }

    @Test
    fun `An aborted test does not save its seed`() {
        val events = execute(AbortedFixture::class)

        events.assertStatistics { it.aborted(1) }
        expectThat(FailedSeeds(directory).load(events.testId())).isNull()
    }

    @Test
    fun `Failed seeds are neither saved nor rerun when switched off`() {
        val events = execute(FailingFixture::class, mode = "off")

        events.assertStatistics { it.failed(1) }
        expectThat(Files.list(directory).count()).isEqualTo(0)
    }

    @Test
    fun `An invalid mode is a configuration error`() {
        val events = execute(FailingFixture::class, mode = "always")

        expectThat(events.singleResult().throwable.orElse(null))
            .isA<VarifyConfigurationException>()
            .get { message }.isEqualTo("Configuration parameter varify.failedSeeds=always must be rerun or off")
    }

    @Test
    fun `An unreadable seed file is ignored and deleted`() {
        val testId = execute(FailingFixture::class, mode = "off").testId()
        val store = FailedSeeds(directory)
        store.save(testId, 1)
        Files.list(directory).use { files -> files.forEach { Files.writeString(it, "not a seed\n") } }

        expectThat(store.load(testId)).isNull()
        expectThat(Files.list(directory).count()).isEqualTo(0)
    }
}
