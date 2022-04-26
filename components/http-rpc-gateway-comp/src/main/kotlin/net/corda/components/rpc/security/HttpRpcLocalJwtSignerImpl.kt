package net.corda.components.rpc.security

import net.corda.crypto.client.CryptoOpsClient
import net.corda.httprpc.server.security.local.HttpRpcLocalJwtSigner
import net.corda.lifecycle.Lifecycle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.charset.StandardCharsets
import java.util.*

@Component(service = [HttpRpcLocalJwtSigner::class])
class HttpRpcLocalJwtSignerImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : HttpRpcLocalJwtSigner, Lifecycle {

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
     * NOTE: this should be changed to pass in a list of claims instead of base 64 string
     *
     * @param payload the jwt claims payload
     *
     * @return base complete jwt token (base64 encoded)
     *
     */
    override fun buildAndSignJwt(payload: String): String {
        val base64Payload = getBase64UrlEncoded(payload)
        return sign("$JWT_HEADER.$base64Payload")
    }

    /**
     * Verifies the JWT object
     *
     * @param token the jwt (base64 encoded, fully formed)
     *
     * @return true or false
     *
     */
    override fun verify(token: String): Boolean {
        val tokenParts = token.split(".")
        if (tokenParts.size != 3) {
            @Suppress("TooGenericExceptionThrown")
            throw Exception("Improper token format")
        }

        val key = cryptoOpsClient.findPublicKey("LOCAL_JWT_SIGNING_KEY", "JWT_KEY")
            ?: @Suppress("TooGenericExceptionThrown")
            throw Exception("Key not found")

        val tempToken = sign("${tokenParts[0]}.${tokenParts[1]}")

        return tempToken == token
    }

    private fun sign(input: String): String {
        var key = cryptoOpsClient.findPublicKey("LOCAL_JWT_SIGNING_KEY", "JWT_KEY")

        if (key == null) {
            key = cryptoOpsClient.generateKeyPair("LOCAL_JWT_SIGNING_KEY", "JWT_KEY", "JWT_KEY")
        }

        return "$input." + Base64.getUrlEncoder()
            .encode(cryptoOpsClient.sign("LOCAL_JWT_SIGNING_KEY", key, input.toByteArray(StandardCharsets.UTF_8)).bytes)
            .toString()
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