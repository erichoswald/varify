---
name: writing-varify-tests
description: Varify property-based tests in Kotlin and JUnit. Use when writing tests with Varify (`VarifyContext`, `some`, `oneOf`), turning example or parameterized tests into varified ones, building generators for domain types, or investigating a failing varified test from its shrunk samples and seed.
---

# Writing Varify tests

Varify runs a test function many times. Each run is an _attempt_ that materializes its values from a `VarifyContext`
right where it needs them. Attempts continue until one fails, the attempt limit (100) is reached, or every combination
of samples has been tried. A failing attempt is _shrunk_ to the simplest samples that fail the same way.

## Setup

Check the build first; add what's missing:

- Test dependency `io.github.erichoswald:varify` (from `mavenLocal()` until it's on Maven Central).
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` 1.7.0+ only for `suspend` test methods.
- The extension, either per class with `@Varify` (or `@ExtendWith(VarifyExtension::class)`), or for all tests in
  `src/test/resources/junit-platform.properties`:
  ```properties
  junit.jupiter.extensions.autodetection.enabled=true
  junit.jupiter.extensions.autodetection.include=varify.junit.VarifyExtension
  ```
- `.varify/` in `.gitignore`: Varify saves the seeds of failed tests there and reruns them first.

Imports: `varify.syntax.*` (`some`, `oneOf`, `collect`, `cover`), `varify.junit.*` (`@Varify`), and the generator
packages `varify.generator.*` (combinators), `.primitive.*`, `.text.*`, `.collection.*`, `.time.*`, `.util.*`.

## Writing a test

1. **State the property**: what holds for _every_ valid input, e.g. a round trip (`decode(encode(x)) == x`), an
   invariant (`sorted` is ordered and a permutation), equivalence with a simpler model, or "never throws".
2. **Declare the context parameter** and materialize each input where the test needs it:
   ```kotlin
   @Test
   context(_: VarifyContext)
   fun `parses what it formats`() {
       val amount = some(long(0, 1_000_000))
       val currency = oneOf(Currency.entries)
       expectThat(Money.parse(Money(amount, currency).format())).isEqualTo(Money(amount, currency))
   }
   ```
   `some(generator)` draws a sample (`a`, `an` and `any` are synonyms for readability); `oneOf(...)` picks from values,
   a collection or a range. Pick generators from [GENERATORS.md](GENERATORS.md).
3. **Constrain at the source**: express valid inputs through generator arguments (`int(1..10)`, `string(char.ascii().letter())`).
   For conditions a generator can't express, reject the attempt with `Assumptions.assumeTrue(condition)` after drawing.
4. **Pass dependencies into generators**: a later value that depends on an earlier one takes it as a generator
   argument, e.g. `some(int(0..<size))`. Then Varify explores and shrinks the dependent value correctly.
5. **Check the distribution** when the interesting cases are rare: `collect("size", list.size.coerceAtMost(3))` reports
   the distribution, `cover("has negatives", list.any { it < 0 }, minPercent = 20)` fails the test when the condition
   is covered less often. Leave leeway below the expected rate, or the test becomes flaky.

The test is done when it passes, the property would fail for a plausible bug (try one mentally or by mutating the code),
and the summary shows the inputs reach the interesting cases.

## Rules of the road

- **Every random choice comes from Varify.** Use generators instead of `Random`, `shuffled()`, `UUID.randomUUID()`,
  `now()` and similar, so that a seed reproduces a failure exactly and shrinking works.
- **Each attempt starts fresh.** All attempts share one test instance, and `@BeforeEach`/`@AfterEach` run once per test
  method. Create mutable fixtures inside the test function.
- **Generators produce distinct values directly.** Varify skips duplicate samples and gives up on a generator after
  1000 duplicates in a row. A lossy lambda like `int().map { it % 10 }` or `compose(int(), int()) { a, b -> a < b }`
  starves the test; use `int(0..9)` or `boolean` instead.
- **Small domains are tested exhaustively.** `oneOf(1..8)`, `boolean` and `enum<E>()` stop after all combinations, which
  the summary reports as "all variations tried".
- **Configuration goes on the annotation:** `@Varify(variationAttemptsLimit = 500)` for more attempts.

## Investigating a failure

1. Read the suppressed `VarifyReport` of the thrown error: it lists the seed, the original samples and the shrunk
   samples. Start from the shrunk ones: they are the simplest that fail the same way (same exception class on the same
   line of the test class).
2. Reproduce: rerunning the test reruns the saved seed first. To pin it while debugging, add `@Varify(seed = "<seed>")`
   and remove it once the bug is fixed.
3. To see every attempt, add `@Varify(eventListeners = [VarifyDebugListener::class])`; `VarifyStatisticsListener`
   reports how many samples were distinct and which were most frequent.
4. Fix the code, or the property if it was wrong, then rerun without the pinned seed.
