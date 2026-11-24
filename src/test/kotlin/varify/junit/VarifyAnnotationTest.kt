package varify.junit

import org.junit.jupiter.api.*
import strikt.api.*
import strikt.assertions.*
import varify.*
import varify.config.*
import varify.engine.*
import varify.event.*
import varify.syntax.*

class VarifyAnnotationTest {

    // Without @ExtendWith: @Varify enables the extension.
    @TestFixture
    class MethodAnnotationFixture {
        @Test
        @Varify(variationAttemptsLimit = 2)
        context(_: VarifyContext)
        fun passes() {
            invocations++
            oneOf(1, 2, 3, 4, 5)
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `A method annotation enables the extension and configures the varification`() {
        executeFixture(MethodAnnotationFixture::class).assertStatistics { it.started(1).succeeded(1) }
        expectThat(MethodAnnotationFixture.invocations).isEqualTo(2)
    }

    // Shrinkable(5) always fails, so the invocations are 1 variation attempt + the shrinking attempts.
    @TestFixture
    @Varify(shrinkAttemptsLimit = 2, seed = "7")
    class PrecedenceFixture {
        @Test
        @Varify(seed = "42")
        context(_: VarifyContext)
        fun fails() {
            invocations++
            throw AssertionError("fails with ${some(Shrinkable(5))}")
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `Each setting is taken from the method, then the class, then configuration parameters`() {
        val events = executeFixture(
            PrecedenceFixture::class,
            mapOf("varify.shrinkAttemptsLimit" to "10", "varify.seed" to "99", "varify.variationAttemptsLimit" to "3"),
        )

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleReport()).contains("seed: 42")
        expectThat(PrecedenceFixture.invocations).isEqualTo(1 + 2)
    }

    @TestFixture
    @Varify(shrinkAttemptsLimit = 2, seed = "7")
    class EnclosingClassFixture {
        @Nested
        @Varify(seed = "42")
        inner class Middle {
            @Nested
            inner class Inner {
                @Test
                context(_: VarifyContext)
                fun fails() {
                    invocations++
                    throw AssertionError("fails with ${some(Shrinkable(5))}")
                }
            }
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `Settings of enclosing classes apply to nested classes, the innermost taking precedence`() {
        val events = executeFixture(EnclosingClassFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleReport()).contains("seed: 42")
        expectThat(EnclosingClassFixture.invocations).isEqualTo(1 + 2)
    }

    @TestFixture
    @Varify
    class ConfigurationParameterFixture {
        @Test
        context(_: VarifyContext)
        fun fails() {
            invocations++
            throw AssertionError("fails with ${some(Shrinkable(5))}")
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `Configuration parameters apply when no annotation sets a value`() {
        val events = executeFixture(
            ConfigurationParameterFixture::class,
            mapOf("varify.shrinkAttemptsLimit" to "2", "varify.seed" to "42"),
        )

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleReport()).contains("seed: 42")
        expectThat(ConfigurationParameterFixture.invocations).isEqualTo(1 + 2)
    }

    @TestFixture
    class RejectedAttemptsLimitFixture {
        @Test
        @Varify(rejectedAttemptsLimit = 1)
        context(_: VarifyContext)
        fun rejects() {
            invocations++
            oneOf(1, 2, 3)
            Assumptions.assumeTrue(false)
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `The rejected attempts limit is configured by the annotation`() {
        executeFixture(RejectedAttemptsLimitFixture::class).assertStatistics { it.started(1).aborted(1) }
        expectThat(RejectedAttemptsLimitFixture.invocations).isEqualTo(1)
    }

    @TestFixture
    class InvalidSeedFixture {
        @Test
        @Varify(seed = "forty-two")
        context(_: VarifyContext)
        fun test() {
            invocations++
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `An invalid seed fails the test without invoking it`() {
        val events = executeFixture(InvalidSeedFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }
            .isA<VarifyConfigurationException>()
            .get { message }.isEqualTo("@Varify(seed = \"forty-two\") must be a long value")
        expectThat(InvalidSeedFixture.invocations).isEqualTo(0)
    }

    @TestFixture
    class InvalidLimitFixture {
        @Test
        @Varify(shrinkAttemptsLimit = -1)
        context(_: VarifyContext)
        fun test() {
        }
    }

    @Test
    fun `A negative limit fails the test`() {
        val events = executeFixture(InvalidLimitFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }
            .isA<VarifyConfigurationException>()
            .get { message }.isEqualTo("@Varify(shrinkAttemptsLimit = -1) must not be negative")
    }

    @Test
    fun `An invalid configuration parameter fails the test`() {
        val events = executeFixture(
            ConfigurationParameterFixture::class,
            mapOf("varify.variationAttemptsLimit" to "lots"),
        )

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }
            .isA<VarifyConfigurationException>()
            .get { message }.isEqualTo("Configuration parameter varify.variationAttemptsLimit=lots must be a positive int")
        expectThat(ConfigurationParameterFixture.invocations).isEqualTo(0)
    }

    object ListenerLog {
        val created = mutableListOf<String>()
        val started = mutableListOf<String>()
    }

    abstract class LoggingListener(private val name: String) : VarifyEventListener {
        init {
            ListenerLog.created += name
        }

        override fun onEvent(event: VarifyEvent) {
            if (event is VarificationStarted) ListenerLog.started += name
        }
    }

    class OuterListener : LoggingListener("outer")
    class ClassListener : LoggingListener("class")
    class MethodListener : LoggingListener("method")

    object ObjectListener : VarifyEventListener {
        override fun onEvent(event: VarifyEvent) {
            if (event is VarificationStarted) ListenerLog.started += "object"
        }
    }

    @TestFixture
    @Varify(eventListeners = [OuterListener::class])
    class ListenerFixture {
        @Nested
        @Varify(eventListeners = [ClassListener::class, OuterListener::class])
        inner class Inner {
            @Test
            @Varify(eventListeners = [MethodListener::class, ObjectListener::class])
            context(_: VarifyContext)
            fun passes() {
                oneOf(1, 2)
            }
        }
    }

    @Test
    fun `Event listeners are collected from the outermost class to the test method, each class once`() {
        executeFixture(ListenerFixture::class).assertStatistics { it.started(1).succeeded(1) }

        expectThat(ListenerLog.created).containsExactly("outer", "class", "method")
        expectThat(ListenerLog.started).containsExactly("outer", "class", "method", "object")
    }

    @Test
    fun `Global event listeners of the configuration parameter come before those of annotations`() {
        val events = executeFixture(
            ListenerFixture::class,
            mapOf("varify.eventListeners" to "${ClassListener::class.java.name}, ${ObjectListener::class.java.name}"),
        )

        events.assertStatistics { it.started(1).succeeded(1) }
        expectThat(ListenerLog.created).containsExactly("class", "outer", "method")
        expectThat(ListenerLog.started).containsExactly("class", "object", "outer", "method")
    }

    @Test
    fun `A global event listener class that does not exist fails the test`() {
        val events = executeFixture(
            ConfigurationParameterFixture::class,
            mapOf("varify.eventListeners" to "com.example.MissingListener"),
        )

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }
            .isA<VarifyConfigurationException>()
            .get { message }
            .isEqualTo("Configuration parameter varify.eventListeners: class com.example.MissingListener not found")
        expectThat(ConfigurationParameterFixture.invocations).isEqualTo(0)
    }

    @Test
    fun `A global event listener class that is not an event listener fails the test`() {
        val events = executeFixture(
            ConfigurationParameterFixture::class,
            mapOf("varify.eventListeners" to "java.lang.String"),
        )

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }
            .isA<VarifyConfigurationException>()
            .get { message }
            .isEqualTo("Configuration parameter varify.eventListeners: class java.lang.String is not a VarifyEventListener")
        expectThat(ConfigurationParameterFixture.invocations).isEqualTo(0)
    }

    @TestFixture
    @Varify(eventListeners = [ClassListener::class])
    class TwoTestMethodsFixture {
        @Test
        context(_: VarifyContext)
        fun first() {
            oneOf(1, 2)
        }

        @Test
        context(_: VarifyContext)
        fun second() {
            oneOf(1, 2)
        }
    }

    @Test
    fun `Each execution of a test method uses new event listener instances`() {
        executeFixture(TwoTestMethodsFixture::class).assertStatistics { it.started(2).succeeded(2) }

        expectThat(ListenerLog.created).containsExactly("class", "class")
        expectThat(ListenerLog.started).containsExactly("class", "class")
    }

    class InvalidListener(
        @Suppress("unused") val name: String,
    ) : VarifyEventListener {
        override fun onEvent(event: VarifyEvent) {}
    }

    @TestFixture
    class InvalidListenerFixture {
        @Test
        @Varify(eventListeners = [InvalidListener::class])
        context(_: VarifyContext)
        fun test() {
            invocations++
        }

        companion object {
            var invocations = 0
        }
    }

    @Test
    fun `An event listener without a constructor without parameters fails the test without invoking it`() {
        val events = executeFixture(InvalidListenerFixture::class)

        events.assertStatistics { it.started(1).failed(1) }
        expectThat(events.singleResult()).get { throwable.orElse(null) }
            .isA<VarifyConfigurationException>()
            .get { message }.isEqualTo(
                "Event listener varify.junit.VarifyAnnotationTest.InvalidListener " +
                    "must be an object or have a constructor without (required) parameters",
            )
        expectThat(InvalidListenerFixture.invocations).isEqualTo(0)
    }

    private fun org.junit.platform.testkit.engine.Events.singleReport(): String =
        singleResult().throwable.orElseThrow().suppressed.filterIsInstance<VarifyReport>().single().message.orEmpty()

    @BeforeEach
    fun resetFixtures() {
        RejectedAttemptsLimitFixture.invocations = 0
        ListenerLog.created.clear()
        ListenerLog.started.clear()
        InvalidListenerFixture.invocations = 0
        MethodAnnotationFixture.invocations = 0
        PrecedenceFixture.invocations = 0
        EnclosingClassFixture.invocations = 0
        ConfigurationParameterFixture.invocations = 0
        InvalidSeedFixture.invocations = 0
    }
}
