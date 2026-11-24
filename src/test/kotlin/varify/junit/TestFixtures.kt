package varify.junit

import org.junit.jupiter.api.extension.*
import org.junit.jupiter.api.extension.ConditionEvaluationResult.*
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.DiscoverySelectors.*
import org.junit.platform.testkit.engine.*
import kotlin.reflect.*

private const val FIXTURES_ENABLED = "varify.test.fixtures.enabled"

/**
 * Marks a test class as a fixture that is only executed by [executeFixture].
 * Regular test runs (Gradle, IDE) report its tests as skipped.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(TestFixtureCondition::class)
annotation class TestFixture

class TestFixtureCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult =
        if (context.getConfigurationParameter(FIXTURES_ENABLED).isPresent) {
            enabled("Executed as test fixture")
        } else {
            disabled("Test fixture, only executed by other tests")
        }
}

/**
 * Executes the tests of a [TestFixture] class with Jupiter and returns the events of its tests.
 * Only the given configuration parameters apply, not those of this repository's `junit-platform.properties`, and
 * failed seeds are off unless given.
 */
fun executeFixture(fixture: KClass<*>, configurationParameters: Map<String, String> = emptyMap()): Events =
    EngineTestKit.engine("junit-jupiter")
        .selectors(selectClass(fixture.java))
        .enableImplicitConfigurationParameters(false)
        .configurationParameter("varify.failedSeeds", "off")
        .configurationParameters(configurationParameters)
        .configurationParameter(FIXTURES_ENABLED, "true")
        .execute()
        .testEvents()

/** The result of the only test that finished. */
fun Events.singleResult(): TestExecutionResult =
    finished().list().single().getRequiredPayload(TestExecutionResult::class.java)
