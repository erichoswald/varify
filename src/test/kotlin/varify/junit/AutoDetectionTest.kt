package varify.junit

import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.*
import strikt.api.*
import strikt.assertions.*
import varify.syntax.*

class AutoDetectionTest {

    // Neither @ExtendWith nor @Varify.
    @TestFixture
    class UnregisteredFixture {
        @Test
        @Suppress("JUnitMalformedDeclaration")
        context(_: VarifyContext)
        fun passes() {
            values += oneOf(1, 2, 3)
        }

        companion object {
            val values = mutableListOf<Int>()
        }
    }

    @Test
    fun `With auto-detection, a test method with a context is varified without registering the extension`() {
        val events = executeFixture(
            UnregisteredFixture::class,
            mapOf(
                "junit.jupiter.extensions.autodetection.enabled" to "true",
                "junit.jupiter.extensions.autodetection.include" to "varify.junit.VarifyExtension",
            ),
        )

        events.assertStatistics { it.started(1).succeeded(1) }
        expectThat(UnregisteredFixture.values).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `Without auto-detection, a test method with a context requires registering the extension`() {
        val events = executeFixture(UnregisteredFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }.isA<ParameterResolutionException>()
        expectThat(UnregisteredFixture.values).isEmpty()
    }

    @Test
    fun `Auto-detection and explicit registration register the extension only once`() {
        val events = executeFixture(
            VarifyAnnotationTest.MethodAnnotationFixture::class,
            mapOf("junit.jupiter.extensions.autodetection.enabled" to "true"),
        )

        events.assertStatistics { it.started(1).succeeded(1) }
    }

    // Relies on this repository's junit-platform.properties.
    @Test
    @Suppress("JUnitMalformedDeclaration")
    context(_: VarifyContext)
    fun `This repository's tests can use a context without registering the extension`() {
        expectThat(oneOf(1, 2, 3)).isContainedIn(listOf(1, 2, 3))
    }

    @BeforeEach
    fun resetFixtures() {
        UnregisteredFixture.values.clear()
    }
}
