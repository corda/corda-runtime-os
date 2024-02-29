package net.corda.sdk.rest

import net.corda.rest.RestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.client.RestClient
import net.corda.rest.client.RestConnectionListener
import net.corda.rest.client.config.RestClientConfig
import net.corda.rest.client.exceptions.ClientSslHandshakeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.MalformedURLException
import java.net.URL
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object RestClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

    private val maxWait: Duration = 10.seconds
    private val cooldownInterval: Duration = 2.seconds

    @Suppress("LongParameterList")
    fun <I : RestResource> createRestClient(
        restResource: KClass<I>,
        apiVersion: RestApiVersion = RestApiVersion.C5_0,
        insecure: Boolean = false,
        minimumServerProtocolVersion: Int = 1,
        username: String = "admin",
        password: String = "admin",
        targetUrl: String = "https://localhost:8888"
    ): RestClient<I> {
        validateTargetUrl(targetUrl)
        val localTargetUrl = if (targetUrl.endsWith("/")) {
            targetUrl.dropLast(1)
        } else {
            targetUrl
        }
        val restClient = RestClient(
            baseAddress = "$localTargetUrl/api/${apiVersion.versionPath}/",
            restResource.java,
            RestClientConfig()
                .enableSSL(true)
                .secureSSL(!insecure)
                .minimumServerProtocolVersion(minimumServerProtocolVersion)
                .username(username)
                .password(password),
            healthCheckInterval = 500
        )

        restClient.addConnectionListener(ConnectionListener())
        return restClient
    }

    private class ConnectionListener<I : RestResource> : RestConnectionListener<I> {
        override fun onConnect(context: RestConnectionListener.RestConnectionContext<I>) {
            // do nothing
        }

        override fun onDisconnect(context: RestConnectionListener.RestConnectionContext<I>) {
            // do nothing
        }

        override fun onPermanentFailure(context: RestConnectionListener.RestConnectionContext<I>) {
            when (context.throwableOpt) {
                is ClientSslHandshakeException -> {
                    errOut.error(
                        "Unable to verify server's SSL certificate. " +
                            "Please check the target parameter or use '--insecure' option."
                    )
                    exitProcess(1)
                }
            }
        }
    }

    private fun validateTargetUrl(url: String) {
        try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Error: Invalid target URL")
        }
    }

    fun <T> executeWithRetry(
        waitDuration: Duration = maxWait,
        timeBetweenAttempts: Duration = cooldownInterval,
        operationName: String,
        block: () -> T
    ): T {
        logger.info("""Performing operation "$operationName"""")
        val endTime = System.currentTimeMillis() + waitDuration.inWholeMilliseconds
        var lastException: Exception?
        do {
            try {
                return block()
            } catch (ex: Exception) {
                lastException = ex
                logger.warn("""Cannot perform operation "$operationName" yet""")
                val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                Thread.sleep(timeBetweenAttempts.inWholeMilliseconds.coerceAtMost(remaining))
            }
        } while (System.currentTimeMillis() <= endTime)

        errOut.error("""Unable to perform operation "$operationName"""", lastException)
        throw lastException!!
    }
}
