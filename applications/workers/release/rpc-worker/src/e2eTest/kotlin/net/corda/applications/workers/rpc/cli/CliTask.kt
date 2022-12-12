package net.corda.applications.workers.rpc.cli

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * Allows running CLI tool as a separate process and obtaining the result produced
 */
object CliTask {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val java = "${System.getProperty("java.home")}${File.separator}bin${File.separator}java"
    private val cliDir = File("${System.getenv("CLI_BUILD_DIR")}${File.separator}cli")

    private fun InputStream.readToStringAsync(): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            BufferedReader(InputStreamReader(this)).lines().collect(Collectors.joining(System.lineSeparator()))
        }
    }

    fun execute(args: List<String>, duration: Duration = Duration.ofMinutes(1)): CliTaskResult {
        require(cliDir.isDirectory)

        val fullCommand = listOf(java, "-jar", "corda-cli.jar") + args
        val fullCommandAsString = fullCommand.joinToString(" ")
        logger.info("Full command: $fullCommandAsString")
        val pb = ProcessBuilder(fullCommand).apply {
            directory(cliDir)
        }
        val process = pb.start()

        val stdOutFuture = process.inputStream.readToStringAsync()
        val stdErrFuture = process.errorStream.readToStringAsync()

        val finished = process.waitFor(duration.seconds.coerceAtLeast(1), TimeUnit.SECONDS)
        require(finished) { "CLI command ($fullCommandAsString) has not finished within: $duration" }

        return CliTaskResult(process.exitValue(), stdOutFuture.get(), stdErrFuture.get())
    }
}