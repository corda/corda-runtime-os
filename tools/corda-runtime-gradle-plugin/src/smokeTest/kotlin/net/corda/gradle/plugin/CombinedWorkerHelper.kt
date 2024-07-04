package net.corda.gradle.plugin

import kotlinx.coroutines.runBlocking
import net.corda.restclient.CordaRestClient
import java.io.File
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

val targetUrl = URI("https://localhost:8888")
const val USER = "admin"
const val PASSWORD = "admin"
const val CORDA_RUNTIME_VERSION_STABLE = "5.3.0.0-HC01"
// Get preTest image tag from the pipeline, or fallback to stable version
val testEnvCordaImageTag = System.getenv("CORDA_IMAGE_TAG") ?: CORDA_RUNTIME_VERSION_STABLE


object CombinedWorkerHelper {
    val restClient = CordaRestClient.createHttpClient(targetUrl, USER, PASSWORD, insecure = true)
    private val composeFile = File(this::class.java.getResource("/config/combined-worker-compose.yml")!!.toURI())

    fun waitUntilRestOrThrow(timeout: Duration = Duration.ofSeconds(120), throwError: Boolean = true) {
        val start = Instant.now()
        while (true) {
            try {
                restClient.helloRestClient.postHello("Test")
                return
            } catch (e: Exception) {
                if (Duration.between(start, Instant.now()) < timeout) {
                    Thread.sleep(10 * 1000)
                    continue
                }
                if (throwError) {
                    throw IllegalStateException("Failed to connect to Corda REST API", e)
                } else return
            }
        }
    }

    private fun runComposeProjectCommand(vararg args: String): Process {
        val composeCommandBase = listOf(
            "docker",
            "compose",
            "-f",
            composeFile.absolutePath,
            "-p",
            "corda-cluster"
        )
        val cmd = composeCommandBase + args.toList()
        val cordaProcessBuilder = ProcessBuilder(cmd)
        cordaProcessBuilder.environment()["CORDA_RUNTIME_VERSION"] = testEnvCordaImageTag
        cordaProcessBuilder.redirectErrorStream(true)
        val process = cordaProcessBuilder.start()
        process.inputStream.transferTo(System.out)

        return process
    }

    fun startCompose(wait: Boolean = true) {
        val cordaProcess = runComposeProjectCommand("up", "--pull", "missing", "--quiet-pull", "--detach")

        val hasExited = cordaProcess.waitFor(10, TimeUnit.SECONDS)
        if (cordaProcess.exitValue() != 0 || !hasExited) {
            throw IllegalStateException("Failed to start Corda cluster using docker compose")
        }

        if (wait) runBlocking { waitUntilRestOrThrow() }

        runComposeProjectCommand("images").waitFor(10, TimeUnit.SECONDS)
    }

    fun stopCompose() {
        val cordaStopProcess = runComposeProjectCommand("down")
        cordaStopProcess.waitFor(30, TimeUnit.SECONDS)
    }
}
