package net.corda.applications.workers.smoketest.websocket.client

import io.javalin.core.util.Header
import java.util.Base64
import net.corda.applications.workers.smoketest.contextLogger
import org.eclipse.jetty.websocket.api.UpgradeRequest
import org.eclipse.jetty.websocket.api.UpgradeResponse
import org.eclipse.jetty.websocket.client.io.UpgradeListener

class BasicAuthUpgradeListener(
    private val userName: String,
    private val password: String
): UpgradeListener {
    private companion object {
        val log = contextLogger()
    }

    override fun onHandshakeRequest(request: UpgradeRequest) {
        val headerValue = toBasicAuthValue(userName, password)
        log.info("Header value: $headerValue")
        request.setHeader(Header.AUTHORIZATION, headerValue)
    }

    override fun onHandshakeResponse(response: UpgradeResponse?) {
    }

    private fun toBasicAuthValue(username: String, password: String): String {
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }
}