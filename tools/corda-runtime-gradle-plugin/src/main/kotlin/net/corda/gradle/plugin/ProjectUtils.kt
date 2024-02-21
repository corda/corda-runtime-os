package net.corda.gradle.plugin

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import kong.unirest.Unirest
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.VirtualNodeInfoDTO
import net.corda.gradle.plugin.dtos.VirtualNodesDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Socket
import java.time.Duration
import java.time.Instant

/**
 * Sleeps the thread for the specified time period.
 * @param millis number of milliseconds to sleep the thread for.
 */
fun rpcWait(millis: Long = 1000) {
    try {
        Thread.sleep(millis)
    } catch (e: InterruptedException) {
        throw UnsupportedOperationException("Interrupts not supported.", e)
    }
}

/**
 * Checks if the given port is in use.
 * @return true if the port is in use, false otherwise.
 */
fun isPortInUse(host: String, port: Int): Boolean {
    return try {
        Socket(host, port)
        true
    } catch (_: ConnectException) {
        false
    }
}

/**
 * Automatically retries the [block] until the operation is successful or [timeout] is reached.
 * @param timeout time to wait for the operation to complete. Default value is 10 seconds.
 * @param cooldown time to wait between retries. Default value is 1 second.
 * @param block the block of code to execute
 * @throws Exception if the operation fails after all retries
 */
fun <R> retry(
    timeout: Duration = Duration.ofMillis(10000),
    cooldown: Duration = Duration.ofMillis(1000),
    block: () -> R
): R {

    var firstException: Exception? = null
    val start = Instant.now()
    var elapsed = Duration.between(start, Instant.now())

    while (elapsed < timeout) {
        try {
            return block()
        } catch (e: Exception) {
            if (firstException == null) {
                firstException = e
            }
            rpcWait(cooldown.toMillis())
            elapsed = Duration.between(start, Instant.now())
        }
    }; throw firstException!!
}

/**
 * Automatically retries the [block] until the operation is successful or max [attempts] are reached.
 * @param attempts a positive number of attempts to retry the operation if fails. Default value is 10 attempts.
 * @param cooldown time to wait between retries. Default value is 1 second.
 * @param block the block of code to execute
 * @throws Exception if the operation fails after all retries
 */
fun <R> retryAttempts(
    attempts: Int = 10,
    cooldown: Duration = Duration.ofMillis(1000),
    block: () -> R
): R {
    require(attempts > 0) { "Number of attempts should be positive" }
    var firstException: Exception? = null

    for (attempt in (1..attempts)) {
        try {
            return block()
        } catch (e: Exception) {
            if (firstException == null) {
                firstException = e
            }
            rpcWait(cooldown.toMillis())
        }
    }; throw firstException!!
}

/**
 * Gets a list of the virtual nodes which have already been created.
 * @Param the [ProjectContext]
 * @return a list of the virtual nodes which have already been created.
 */
fun getExistingNodes(pc: ProjectContext) : List<VirtualNodeInfoDTO> {

    Unirest.config().verifySsl(false)
    val mapper = ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val response: HttpResponse<JsonNode> = Unirest.get(pc.cordaClusterURL + "/api/v1/virtualnode")
        .basicAuth(pc.cordaRestUser, pc.cordaRestPassword)
        .asJson()

    if (response.status != HttpURLConnection.HTTP_OK) {
        throw CordaRuntimeGradlePluginException("Failed to get Existing vNodes, response status: " + response.status)
    }

    return try {
        mapper.readValue(response.body.toString(), VirtualNodesDTO::class.java).virtualNodes!!
    } catch (e: Exception) {
        throw CordaRuntimeGradlePluginException("Failed to get Existing vNodes with exception: ${e.message}", e)
    }
}
