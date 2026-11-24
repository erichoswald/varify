package varify.junit

import org.junit.jupiter.api.extension.*
import varify.config.*
import java.nio.file.*
import java.security.*

private const val FAILED_SEEDS = "varify.failedSeeds"
private const val FAILED_SEEDS_DIRECTORY = "varify.failedSeedsDirectory"
private const val DEFAULT_DIRECTORY = ".varify/failed-seeds"

/**
 * The seeds of failed tests, one file per test in [directory], named by a hash of the test's unique ID. Each file
 * contains the seed and, for humans, the unique ID.
 */
internal class FailedSeeds(private val directory: Path) {

    fun load(testId: String): Long? =
        fileOf(testId)
            .takeIf(Files::exists)
            ?.let(Files::readAllLines)
            ?.firstOrNull()
            ?.toLongOrNull()
            ?: null.also { Files.deleteIfExists(fileOf(testId)) } // Delete unreadable files.

    fun save(testId: String, seed: Long) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "seed", ".tmp")
        Files.writeString(temporary, "$seed\n$testId\n")
        Files.move(temporary, fileOf(testId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun delete(testId: String) {
        Files.deleteIfExists(fileOf(testId))
    }

    private fun fileOf(testId: String): Path {
        val hash = MessageDigest.getInstance("SHA-256").digest(testId.toByteArray())
        return directory.resolve(hash.joinToString("") { "%02x".format(it) })
    }
}

/**
 * The failed seeds to rerun, or `null` if the configuration parameter `varify.failedSeeds` disables failed seeds.
 */
internal fun ExtensionContext.failedSeeds(): FailedSeeds? {
    val mode = getConfigurationParameter(FAILED_SEEDS).orElse(null) ?: "rerun"
    checkConfiguration(mode == "rerun" || mode == "off") {
        "Configuration parameter $FAILED_SEEDS=$mode must be rerun or off"
    }
    return if (mode == "off") {
        null
    } else {
        val directory = getConfigurationParameter(FAILED_SEEDS_DIRECTORY).orElse(null) ?: DEFAULT_DIRECTORY
        FailedSeeds(Path.of(directory))
    }
}
