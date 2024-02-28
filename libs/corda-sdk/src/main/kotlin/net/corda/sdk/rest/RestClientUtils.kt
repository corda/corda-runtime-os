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

object RestClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

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

    private fun validateTargetUrl(url: String) {
        try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Error: Invalid target URL")
        }
    }
}
