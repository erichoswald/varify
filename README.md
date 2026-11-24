# Varify

_Varify_ is an artificial word that combines two ideas:
1. _verify_
2. _variate_

Varify is a library that allows a single test function to run the same scenario with slightly different values without having to duplicate the test function.

A typical test function is a procedure without input parameters and without return value. Its goal is to falsify a hypothesis and, as a side effect, throw an assertion error if it succeeds in doing so. Any value it needs must either be inherited from its context (say, a test class) or created on the spot.

Many test runners relax this restriction by supporting parameterized test functions and passing different values as input parameters. They can read values for these parameters from various sources, including annotation parameters and factory methods. Property-based testing expands on this idea and populates input values using generators that can provide randomized data.

Varify adds parameterized and random values, too. However, instead of adding arguments to the testing function and selecting the desired source or generator with annotations, Varify runs the testing function within a _varification context_. Calling the `some` method in this context and passing it the desired value source allows the testing function to materialize from the context right when and where they are needed. 

```kotlin
fun `test function`() = varify {
    val a = some(int(-10..10))
    val b = some(int(-10..10))
    val result = systemUnderTest.add(a, b)
    assertThat(result).isEqualTo(a + b)
}
```

This has the following advantages over parameterized tests:
* Better locality because values can be materialized right where they are needed.
* Improved readability because value sources can be given meaningful names.
* More concise test code because value sources need not be declared as parameters (less pollution caused by technical limitations)

As with property-based testing, Varify uses generators to provide randomized data. This allows the testing function to focus on the behavior of the system under test rather than on the details of the input values. If using random values in tests sounds scary, consider this:
* You can configure Varify to use a specific random seed. Besides, the random seed that was used is available after each run. This allows you to repeat a test with exactly the same generated data and makes it easier to deal with flaky tests.
* Varify applies shrinking, meaning it attempts to find a minimal sample when it detects a failed test.
* Varify detects when it runs out of unique values and stops further attempts to make the test function fail.

Varify runs a test function with different values until it fails, until it reaches a configured number of attempts without failing, or until it runs out of unique values.

## Installation

Varify isn't published to Maven Central yet. To use it in another project, publish it to your local Maven repository:

```shell
./gradlew publishToMavenLocal
```

Then add it as a test dependency:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("io.github.erichoswald:varify:0.1.0")
    // Only needed for suspending test methods (1.7.0 or later):
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
```

## Integration

The `varify` function can be called from any code, independent of which test runner is used. That said, many tools
integrate JUnit really well, which is why Varify also includes additional support for running within JUnit.

## JUnit

For JUnit, Varify comes with an extension (`VarifyExtension`) that varifies test methods with a `VarifyContext`
parameter or context parameter, e.g. `context(_: VarifyContext) fun test() { ... }`.

### Enabling the extension

Either annotate test classes or methods with `@Varify` (or `@ExtendWith(VarifyExtension::class)`), or enable it for
all tests with JUnit's extension auto-detection in `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
# Optional: ignore extensions that other libraries register.
junit.jupiter.extensions.autodetection.include=varify.junit.VarifyExtension
```

The extension only varifies test methods with a `VarifyContext` parameter; all other tests run as usual.

### Writing test methods

The preferred style is a context parameter, which makes `some`, `oneOf` etc. available directly:

```kotlin
@Test
context(_: VarifyContext)
fun `adds numbers`() {
    val a = some(int(-10..10))
    val b = some(int(-10..10))
    expectThat(systemUnderTest.add(a, b)).isEqualTo(a + b)
}
```

With a regular parameter, bring it into context with `context(varify) { ... }`:

```kotlin
@Test
fun `adds numbers`(varify: VarifyContext) {
    context(varify) {
        val a = some(int())
        // ...
    }
}
```

Suspending test methods are supported, too; each attempt runs in its own `runTest`. They require
`org.jetbrains.kotlinx:kotlinx-coroutines-test` 1.7.0 or later as a test dependency, which Varify doesn't bring along.

Each test method is reported as a single test, no matter how many attempts Varify makes. All attempts share one
test instance, and `@BeforeEach`/`@AfterEach` methods run once per test method, not once per attempt. Failed assumptions
(e.g. `Assumptions.assumeTrue`) reject the current attempt, and the test is only aborted if every attempt is rejected.

On success, Varify publishes a summary (attempts and seed) as a report entry of the test. On failure, the summary
is attached to the thrown error as a suppressed exception, including the seed and the original and shrunk samples.

### Configuration

`@Varify` configures the attempt limits, the seed, and event listeners:

```kotlin
@Varify(variationAttemptsLimit = 500, seed = "42")
```

| Setting                  | Configuration parameter         | Default | Meaning                                                 |
|--------------------------|---------------------------------|---------|---------------------------------------------------------|
| `variationAttemptsLimit` | `varify.variationAttemptsLimit` | 100     | Maximum number of variation attempts                    |
| `rejectedAttemptsLimit`  | `varify.rejectedAttemptsLimit`  | 500     | Maximum number of attempts rejected by assumptions      |
| `shrinkAttemptsLimit`    | `varify.shrinkAttemptsLimit`    | 1000    | Maximum number of shrinking attempts                    |
| `seed`                   | `varify.seed`                   | random  | Random seed (a string because every `Long` is valid)    |
| `eventListeners`         | `varify.eventListeners`         | none    | Event listener classes (comma-separated class names)    |

Each setting is taken from the first source that sets it:
1. the test method,
2. the test class (or its superclasses),
3. the enclosing classes of a `@Nested` test class, from innermost to outermost,
4. the JUnit configuration parameters, e.g. in `junit-platform.properties` or as system properties (`-Dvarify.seed=42`),
5. the defaults.

Event listeners are collected instead: first those of `varify.eventListeners`, then those of all annotations from the
outermost class to the test method, each class only once. Listener classes must be Kotlin objects or have a constructor
without (required) parameters.

### Failed seeds

When a varified test fails, Varify saves its seed in `.varify/failed-seeds`, and the next execution of the test first
reruns that seed. If the rerun passes, the seed is deleted and the test continues with a random seed.
Add `.varify/` to your `.gitignore`.

| Configuration parameter       | Default                | Meaning                                 |
|-------------------------------|------------------------|-----------------------------------------|
| `varify.failedSeeds`          | `rerun`                | `rerun` or `off` (e.g. on CI)           |
| `varify.failedSeedsDirectory` | `.varify/failed-seeds` | Directory for the seeds of failed tests |

Tests with a seed set by `@Varify` or `varify.seed` neither save nor rerun seeds.

## Generators

Generators provide the samples of a test function, e.g. `some(int())`. They yield their most interesting samples first
(e.g. 0, 1, -1 and the bounds of a range), then random ones, and each sample knows how to shrink towards a simpler one.
All generators are in subpackages of `varify.generator`.

### Values and primitives

| Generator                                                    | Samples                                                        |
|--------------------------------------------------------------|----------------------------------------------------------------|
| `from(1, 2, 3)`, `from(list)`, `from(1..10)`                 | The given values, in random order; ranges like `int`, `long` or `char` |
| `enum<E>()`, `boolean`                                       | All values of an enum, `false` and `true`                      |
| `int()`, `int(0..9)`, `int(min, max)`                        | Ints, by default all; shrink towards 0 (or the closest bound)  |
| `int.nonNegative(max)`, `int.positive(max)`                  | Ints from 0 or 1 to `max`                                      |
| `long(...)`, `long.nonNegative(max)`, `long.positive(max)`   | Longs, like `int`                                              |
| `char()`, `char('a'..'z', '0'..'9')`, `char("+-*")`          | Chars, by default all except surrogates                        |
| `char.ascii()`, `char.latin1()`, `char.hex()`                | Chars of common sets                                           |
| `codePoint()`, `codePoint.ascii()`, `codePoint.bmp()`, ...   | Code points (`Int`), like `char` but including those above `0xFFFF` |

Char and code point generators can be narrowed with `letter()`, `digit()`, `letterOrDigit()`, `lowerCase()`,
`upperCase()`, `whitespace()`, `printable()`, `filter { ... }` and `filterNot { ... }`, and char generators combined
with `+`, e.g. `char.ascii().letter() + "_"`.

### Text, collections and other types

| Generator                                                  | Samples                                                          |
|------------------------------------------------------------|------------------------------------------------------------------|
| `string()`, `string.ofLength(int(1..10))`                  | Strings of mostly printable code points, by default up to 100 long |
| `string(char.ascii().letter())`, `string(codePoint(), length)` | Strings of the given chars or code points                    |
| `join(segments, count, separator, prefix, postfix)`        | Joined strings, e.g. `join(string(char.hex()), separator = ".")` |
| `list(elements)`, `list(elements, int(1..5))`              | Lists, by default with up to 100 elements                        |
| `set(elements)`, `map(keys, values)`                       | Sets and maps, with an optional size generator like `list`       |
| `uuid()`                                                   | Random (version 4) UUIDs                                         |

Collections shrink to fewer elements first, then to smaller elements. Strings shrink like lists, towards `a`.

### Date and time

Generators exist for the main `java.time` types: `localDate()`, `localTime()`, `localDateTime()`, `instant()`,
`offsetDateTime()`, `zonedDateTime()`, `year()`, `yearMonth()`, `monthDay()`, `month()`, `dayOfWeek()`, `duration()`,
`period()`, `zoneOffset()` and `zoneId()`.

- By default, dates are in the years 1900 to 2100, and samples shrink towards 1970-01-01T00:00 (or zero for durations
  and periods).
- All accept a range, either as `min` and `max` or as `ClosedRange`, e.g. `localDate(LocalDate.now()..LocalDate.now().plusYears(1))`.
- Types with a time accept a `precision`, by default `ChronoUnit.SECONDS`, e.g. `instant(precision = ChronoUnit.MILLIS)`.
- Special values include leap days, the turn of the millennium, and zones with daylight saving time or offsets that
  aren't whole hours. About a quarter of the samples of `zonedDateTime()` are close to a transition of their zone.
- `offsetDateTime` and `zonedDateTime` take a generator for their offsets or zones, e.g.
  `zonedDateTime(zone = from(ZoneId.of("Europe/Zurich")))`.

Kotlin time types can be generated by mapping, e.g. `duration().map { it.toKotlinDuration() }` or, with
kotlinx-datetime, `localDate().map { it.toKotlinLocalDate() }`.

### Combining generators

| Combinator                                         | Samples                                                                |
|----------------------------------------------------|------------------------------------------------------------------------|
| `generator.map { ... }`                            | Transformed samples, which shrink through their source samples         |
| `compose(a, b) { x, y -> ... }` (up to five)       | Values combined from several generators                                |
| `pair(a, b)`                                       | Pairs                                                                  |
| `mix(a, b)`, `mix.weighted(a to 9, b to 1)`        | The samples of several alternatives, with equal or weighted chances    |
| `generator.orNull()`                               | `null` first, then the samples of the generator                        |

```kotlin
enum class Currency { CHF, EUR, USD }
data class Money(val amount: Long, val currency: Currency)

val money = compose(long(0, 1_000_000), enum<Currency>(), ::Money)
```

## Custom generators

### Combining existing generators

Most generators for domain types can be built from existing ones with `map`, `compose`, `mix` and the collection
generators, as in the `money` example above. They shrink automatically through their sources.

Varify identifies generators by their key, so that it recognizes the same generator in the next attempt. The key of a
`map` or `compose` generator includes its lambda's class, i.e. its call site, but not the values the lambda captures.
When a captured value is an earlier sample that shrinks, Varify rebuilds the later sample from its source samples with
the current lambda, but doesn't explore other source samples. Pass such dependencies to a generator instead:

```kotlin
val size = some(int(1..10))
val index = some(int(0..<size))                // Explored: the range is part of the key.
val other = some(int(0..99).map { it % size }) // Not explored: `size` is only captured.
```

Varify skips duplicate values, so a lambda that maps many source values to a few values, e.g. `int().map { it % 10 }`
or `compose(int(), int()) { a, b -> a < b }`, produces mostly samples that are skipped. After 1000 successive
duplicates, Varify considers the generator exhausted. Until then, it spends time on generating duplicates, and values
that the lambda rarely produces may not appear at all. Prefer a source that generates the values you need directly,
e.g. `int(0..9)` or `boolean`.

### Implementing `Generator`

If a type's samples or shrinking can't be expressed by combining generators, implement `Generator` and `Sample`:

```kotlin
data class PowerOfTwoGenerator(val maxExponent: Int = 30) : Generator<Int> {
    override val key = generatorKey<PowerOfTwoGenerator>(maxExponent)

    override fun generate(randomizer: Randomizer): Sequence<Sample<Int>> =
        randomizer.shuffle(0..maxExponent).asSequence().map(::PowerOfTwoSample)

    private class PowerOfTwoSample(val exponent: Int) : Sample<Int>() {
        override val value = 1 shl exponent

        override fun shrink(): List<Sample<Int>> = (0..<exponent).map(::PowerOfTwoSample)
    }
}
```

(This one could also be `int(0..maxExponent).map { 1 shl it }`.)

- **`key`** consists of the generator type and all properties that determine its samples, including the keys of source
  generators. Generators with equal keys must generate the same samples.
- **`generate`** returns the samples in the order Varify should try them, the most interesting ones first. It must be
  deterministic for a given randomizer, so use only the `Randomizer` for random choices. The sequence may be infinite;
  if it is finite, Varify stops once all combinations have been tried. Duplicate values are skipped, and after 1000
  successive duplicates, the generator counts as exhausted, so generators shouldn't repeat their values randomly.
- **`Sample.shrink`** returns simpler candidates, the most aggressive first: Varify continues with the first candidate
  that still fails the test. Candidates must differ from the sample, and repeated shrinking must end.
- **`rebuild`** only needs to be overridden by generators that derive their samples from other generators, like `map`
  and `compose` do.

## Statistics

### Collecting values and requiring coverage

Random values only find bugs if they reach the interesting cases. To check this, test functions can collect values and
require conditions to be covered:

```kotlin
context(_: VarifyContext)
fun `sorts lists`() {
    val list = some(list(int()))
    collect("size", list.size.coerceAtMost(3))
    cover("negative values", list.any { it < 0 }, minPercent = 20)
    expectThat(list.sorted()).isSorted()
}
```

The summary reports the distribution of the collected values, sorted by frequency with at most ten values per label,
and the coverage of each condition:

```
Varify: passed 100 attempts, attempt limit reached
  seed: 42
  collected size: 3 (61%), 0 (14%), 2 (13%), 1 (12%)
  covered negative values: 48% (required 20%)
```

A test can collect or cover several values per attempt, e.g. in a loop. Calls count in passing
and failing variation attempts, but not in attempts rejected by an assumption or in shrinking attempts.

If all attempts pass but a condition is covered in less than its required percentage of calls, the test fails with an
`AssertionFailedError`. The closer the prescribed threshold is to the expected percentage, the more likely the test
will fail by chance and become flaky. Keep some leeway between prescribed and expected rate, or increase the number
of attempts.

### Debug and statistics listeners

Two built-in event listeners help to understand what Varify does. Enable them for a test with
`@Varify(eventListeners = [...])`, or for all tests with the configuration parameter `varify.eventListeners`. They
publish their output as report entries of the test. Both describe generators by their keys, which
include all properties of a generator; the examples below shorten them.

`VarifyDebugListener` logs each attempt with its outcome and samples. During shrinking, `*` marks the sample whose
shrink candidate the attempt tries:

```
Varify debug: seed 42
variation 1: failed (AssertionError: fails with 1 3)
  [0] IntGenerator(0..1, ...) = 1
  [1] IntGenerator(0..3, ...) = 3
shrink 1: diverged (IllegalStateException: different)
 *[0] IntGenerator(0..1, ...) = 0
  [1] IntGenerator(0..3, ...) = 3
shrink 2: failed the same way (AssertionError: fails with 1 2)
  [0] IntGenerator(0..1, ...) = 1
 *[1] IntGenerator(0..3, ...) = 2
shrinking exhausted after 2 attempts
```

`VarifyStatisticsListener` reports the attempts and duration of each stage, and for the samples of each position and
generator how many were distinct and which values were most frequent:

```
Varify statistics: seed 42
variation: 100 attempts (12 rejected), attempt limit reached, 35 ms
samples:
  [0] IntGenerator(0..100, ...): 100 samples, 97 distinct, most frequent: 0 (2), 1 (2)
  [1] StringGenerator(...): 100 samples, 61 distinct, most frequent: "" (5)
```

## License

Varify is licensed under the [Apache License, Version 2.0](LICENSE).
