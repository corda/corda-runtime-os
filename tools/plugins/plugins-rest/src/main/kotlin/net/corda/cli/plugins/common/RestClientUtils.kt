package net.corda.cli.plugins.common

import net.corda.rest.RestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.client.RestClient
import net.corda.rest.client.RestConnectionListener
import net.corda.rest.client.config.RestClientConfig
import net.corda.rest.client.exceptions.ClientSslHandshakeException
import net.corda.rest.exception.ResourceAlreadyExistsException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.MalformedURLException
import java.net.URL
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.system.exitProcess

object RestClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

    fun <I : RestResource> RestCommand.createRestClient(
        restResource: KClass<I>,
        apiVersion: RestApiVersion = RestApiVersion.C5_0
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
        override fun onConnect(context: RestConnectionListener.RestConnectionContext<I>) {}

        override fun onDisconnect(context: RestConnectionListener.RestConnectionContext<I>) {}

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

    fun <T> executeWithRetry(
        waitDuration: Duration,
        operationName: String,
        onAlreadyExists: (ResourceAlreadyExistsException) -> T = ::reThrow,
        block: () -> T
    ): T {
        logger.info("""Performing operation "$operationName"""")
        val endTime = System.currentTimeMillis() + waitDuration.toMillis()
        var lastException: Exception?
        var sleep = 1000L
        do {
            try {
                return block()
            } catch (ex: ResourceAlreadyExistsException) {
                return onAlreadyExists(ex)
            } catch (ex: Exception) {
                lastException = ex
                logger.warn("""Cannot perform operation "$operationName" yet""")
                val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                Thread.sleep(sleep.coerceAtMost(remaining))
                sleep *= 2
            }
        } while (System.currentTimeMillis() <= endTime)

        errOut.error("""Unable to perform operation "$operationName"""", lastException)
        throw lastException!!
    }

    private fun reThrow(ex: ResourceAlreadyExistsException): Nothing {
        logger.info("Re-throwing", ex)
        throw ex
    }

    private fun validateTargetUrl(url: String) {
        try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Error: Invalid target URL")
        }
    }
}
