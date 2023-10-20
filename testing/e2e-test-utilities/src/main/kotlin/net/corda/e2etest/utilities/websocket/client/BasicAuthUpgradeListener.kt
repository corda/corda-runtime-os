package net.corda.e2etest.utilities.websocket.client

import org.eclipse.jetty.websocket.api.UpgradeRequest
import org.eclipse.jetty.websocket.api.UpgradeResponse
import org.eclipse.jetty.websocket.client.io.UpgradeListener
import org.slf4j.LoggerFactory
import java.util.Base64

class BasicAuthUpgradeListener(
    private val userName: String,
    private val password: String
) : UpgradeListener {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val AUTHORIZATION_HEADER = "Authorization"
    }

    override fun onHandshakeRequest(request: UpgradeRequest) {
        val headerValue = toBasicAuthValue(userName, password)
        log.info("Header value: $headerValue")
        request.setHeader(AUTHORIZATION_HEADER, headerValue)
    }

    override fun onHandshakeResponse(response: UpgradeResponse?) {
    }

    private fun toBasicAuthValue(username: String, password: String): String {
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }
}