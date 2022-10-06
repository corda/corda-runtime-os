package net.corda.cli.plugins.common

import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient
import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.reflect.KClass

object HttpRpcClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

    fun <I : RpcOps> HttpRpcCommand.createHttpRpcClient(rpcOps: KClass<I>): HttpRpcClient<I> {
        val localTargetUrl = if(targetUrl.endsWith("/")) {
            targetUrl.dropLast(1)
        } else {
            targetUrl
        }
        return HttpRpcClient(
            baseAddress = "$localTargetUrl/api/v1/",
            rpcOps.java,
            HttpRpcClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(minimumServerProtocolVersion)
                .username(username)
                .password(password),
            healthCheckInterval = 500
        )
    }

    fun <T> executeWithRetry(
        waitDuration: Duration, operationName: String,
        onAlreadyExists: (ResourceAlreadyExistsException) -> T = { throw it },
        block: () -> T
    ): T {
        logger.info("Performing $operationName")
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
                logger.warn("Cannot perform $operationName yet", ex)
                val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                Thread.sleep(sleep.coerceAtMost(remaining))
                sleep *= 2
            }
        } while (System.currentTimeMillis() <= endTime)

        errOut.error("Unable to perform $operationName", lastException)
        throw lastException!!
    }

    fun ignore(ex: ResourceAlreadyExistsException) {
        logger.debug("Ignoring", ex)
    }
}