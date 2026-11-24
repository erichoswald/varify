# Varify generators

Generators yield their most interesting samples first (0, 1, -1, range bounds, empty collections), then random ones,
and each sample shrinks towards a simpler one.

## Values and primitives (`varify.generator`, `varify.generator.primitive`)

| Generator                                                    | Samples                                                     |
|--------------------------------------------------------------|-------------------------------------------------------------|
| `from(1, 2, 3)`, `from(list)`, `from(1..10)`                 | The given values in random order                            |
| `enum<E>()`, `boolean`                                       | All enum values; `false` and `true`                         |
| `int()`, `int(0..9)`, `int(min, max)`                        | Ints, by default all; shrink towards 0 or the closest bound |
| `int.nonNegative(max)`, `int.positive(max)`                  | Ints from 0 or 1 to `max`                                   |
| `long(...)`, `long.nonNegative(max)`, `long.positive(max)`   | Longs, like `int`                                           |
| `char()`, `char('a'..'z', '0'..'9')`, `char("+-*")`          | Chars, by default all except surrogates                     |
| `char.ascii()`, `char.latin1()`, `char.hex()`                | Chars of common sets                                        |
| `codePoint()`, `codePoint.ascii()`, `codePoint.bmp()`        | Code points (`Int`), including those above `0xFFFF`         |

Narrow char and code point generators with `letter()`, `digit()`, `letterOrDigit()`, `lowerCase()`, `upperCase()`,
`whitespace()`, `printable()`, `filter { }` and `filterNot { }`; combine char generators with `+`, e.g.
`char.ascii().letter() + "_"`.

## Text, collections and others (`.text`, `.collection`, `.util`)

| Generator                                                      | Samples                                                     |
|----------------------------------------------------------------|-------------------------------------------------------------|
| `string()`, `string.ofLength(int(1..10))`                      | Strings of mostly printable code points, up to 100 long     |
| `string(char.ascii().letter())`, `string(codePoint(), length)` | Strings of the given chars or code points                   |
| `join(segments, count, separator, prefix, postfix)`            | Joined strings, e.g. `join(string(char.hex()), separator = ".")` |
| `list(elements)`, `list(elements, int(1..5))`                  | Lists, by default up to 100 elements                        |
| `set(elements)`, `map(keys, values)`                           | Sets and maps, with an optional size generator              |
| `uuid()`                                                       | Random (version 4) UUIDs                                    |

Collections shrink to fewer elements first, then to smaller elements.

## Date and time (`.time`)

`localDate()`, `localTime()`, `localDateTime()`, `instant()`, `offsetDateTime()`, `zonedDateTime()`, `year()`,
`yearMonth()`, `monthDay()`, `month()`, `dayOfWeek()`, `duration()`, `period()`, `zoneOffset()`, `zoneId()`.

- Default years 1900 to 2100; samples shrink towards 1970-01-01T00:00 (zero for durations and periods).
- Ranges as `min`/`max` or `ClosedRange`, e.g. `localDate(start..end)`.
- Types with a time take `precision` (default `ChronoUnit.SECONDS`).
- `offsetDateTime(offset = ...)` and `zonedDateTime(zone = ...)` take a generator, e.g.
  `zonedDateTime(zone = from(ZoneId.of("Europe/Zurich")))`.
- Kotlin types by mapping: `duration().map { it.toKotlinDuration() }`.

## Combinators (`varify.generator`)

| Combinator                                  | Samples                                                   |
|---------------------------------------------|-----------------------------------------------------------|
| `generator.map { ... }`                     | Transformed samples, shrinking through their source       |
| `compose(a, b) { x, y -> ... }` (up to five) | Values combined from several generators                  |
| `pair(a, b)`                                | Pairs                                                     |
| `mix(a, b)`, `mix.weighted(a to 9, b to 1)` | Samples of several alternatives; put the simplest first   |
| `generator.orNull()`                        | `null` first, then the generator's samples                |

Domain types are built from these, and shrink automatically:

```kotlin
val money = compose(long(0, 1_000_000), enum<Currency>(), ::Money)
val order = compose(list(money, int(1..5)), enum<OrderStatus>(), ::Order)
```

Keep `map`/`compose` lambdas close to one-to-one (see the duplicates rule in [SKILL.md](SKILL.md)). Implement
`Generator` and `Sample` only when combinators can't express the samples or their shrinking; see the Varify README.
