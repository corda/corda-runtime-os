package net.corda.cli.plugins.common

import net.corda.rest.RestResource
import net.corda.rest.client.RestClient
import net.corda.rest.client.config.RestClientConfig
import net.corda.rest.exception.ResourceAlreadyExistsException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.reflect.KClass

object RestClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

    fun <I : RestResource> RestCommand.createHttpRpcClient(rpcOps: KClass<I>): RestClient<I> {
        val localTargetUrl = if(targetUrl.endsWith("/")) {
            targetUrl.dropLast(1)
        } else {
            targetUrl
        }
        return RestClient(
            baseAddress = "$localTargetUrl/api/v1/",
            rpcOps.java,
            RestClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(minimumServerProtocolVersion)
                .username(username)
                .password(password),
            healthCheckInterval = 500
        )
    }

    fun <T> executeWithRetry(
        waitDuration: Duration, operationName: String,
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

    fun reThrow(ex: ResourceAlreadyExistsException): Nothing {
        logger.info("Re-throwing", ex)
        throw ex
    }

    fun ignore(ex: ResourceAlreadyExistsException) {
        logger.debug("Ignoring", ex)
    }
}