package net.corda.components.rpc.security

import net.corda.crypto.client.CryptoOpsClient
import net.corda.lifecycle.Lifecycle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.charset.StandardCharsets
import java.util.*

@Component(service = [HttpRpcLocalJwtSigner::class])
class HttpRpcLocalJwtSigner @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : Lifecycle {

    private val JWT_HEADER = getBase64UrlEncoded(
        """
        {
            "alg": "ES256",
            "typ": "JWT"
        }
    """.trimIndent()
    )

    /**
     * Signs the JWT object
     *
     * @param payload the jwt claims payload (base64 encoded)
     *
     * @return base complete jwt token (base64 encoded)
     *
     */
    fun buildAndSignJwt(payload: String): String {
        return sign(JWT_HEADER + "." + payload)
    }

    private fun sign(input: String): String {
//        var key = cryptoOpsClient.findHSMKey("tempTennantId", "JWT_KEY").

        val key = cryptoOpsClient.generateKeyPair("tempTennantId", "JWT_KEY", "JWT_KEY")
        return Base64.getUrlEncoder().encode(cryptoOpsClient.sign("tempTennantId", key, input.toByteArray(StandardCharsets.UTF_8)).bytes).toString()
    }

    private fun getBase64UrlEncoded(input: String): String {
        return Base64.getUrlEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
    }

    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}