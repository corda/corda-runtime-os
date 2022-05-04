package net.corda.components.rpc.security

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.server.security.local.HttpRpcLocalJwtSigner
import net.corda.lifecycle.Lifecycle
import net.corda.v5.cipher.suite.KeyEncodingService
import org.json.JSONObject
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Component(service = [HttpRpcLocalJwtSigner::class])
class HttpRpcLocalJwtSignerImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    val keyEncodingService: KeyEncodingService
) : HttpRpcLocalJwtSigner, Lifecycle {

    private val JWT_HEADER = base64EncodeJsonMap(
        mapOf("alg" to "ES256", "typ" to "JWT")
    )
    private val TENANT_ID = "LOCAL_JWT_SIGNING_KEY"
    private val CATEGORY = "JWT_KEY"
    private val ALIAS = "JWT_KEY"

    /**
     * Signs the JWT object
     *
     * NOTE: this should be changed to pass in a list of claims instead of base 64 string
     *
     * @param claims the jwt claims payload
     *
     * @return base complete jwt token (base64 encoded)
     *
     */
    override fun buildAndSignJwt(claims: Map<String, String>): String {
        val base64Payload = base64EncodeJsonMap(claims)
        return sign("$JWT_HEADER.$base64Payload")
    }

    /**
     * Verifies the JWT object
     *
     * @param token the jwt (base64 encoded, fully formed)
     *
     * @return true or false
     */
    override fun verify(token: String): Boolean {
        val tokenParts = token.split(".")
        if (tokenParts.size != 3) {
            @Suppress("TooGenericExceptionThrown")
            throw Exception("Improper token format")
        }

        verifyTokenDateFields(tokenParts[1])

        val key = retrieveJwtSigningKey()
            ?: @Suppress("TooGenericExceptionThrown")
            throw Exception("Key not found")

        val tempToken = sign("${tokenParts[0]}.${tokenParts[1]}", key)

        return tempToken == token
    }

    /**
     * Decodes the claims token part and verifies the date fields
     *
     * @param claims the claims token part(base64 encoded)
     *
     * @return true or false
     */
    private fun verifyTokenDateFields(claims: String): Boolean {

        val claimsMap = base64DecodeToMap(claims)

        if (claimsMap.containsKey("exp") && claimsMap.containsKey("iat") && claimsMap.containsKey("nbf")) {
            val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            val iat = claimsMap["iat"].toString().toLong()
            val exp = claimsMap["exp"].toString().toLong()
            val nbf = claimsMap["nbf"].toString().toLong()

            if (iat < now) return false
            if (exp < now) return false
            if (nbf > now) return false
        } else {
            throw java.lang.Exception("Missing claims")
        }
        return true
    }

    /**
     * Attempts to fetch the signing key,
     * if not generates one and then signs the JWT object.
     *
     * @param input the jwt (base64 encoded, fully formed)
     *
     * @return the signed JWT token
     */
    private fun sign(input: String): String {
        var key = retrieveJwtSigningKey()

        if (key == null) {
            key = cryptoOpsClient.generateKeyPair(TENANT_ID, CATEGORY, ALIAS)
        }
        return sign(input, key)
    }

    /**
     * signs the JWT object with the supplied key.
     *
     * @param input the jwt (base64 encoded, fully formed)
     *
     * @return the signed JWT token
     */
    private fun sign(input: String, key: PublicKey): String {
        return "$input." + Base64.getUrlEncoder()
            .encode(cryptoOpsClient.sign(TENANT_ID, key, input.toByteArray(StandardCharsets.UTF_8)).bytes)
            .toString()
    }

    /**
     * Attempts to retrieve the JWT signing key from
     * the HSM
     *
     * @return The Public key used to sign JWT tokens or null
     */
    private fun retrieveJwtSigningKey(): PublicKey? {
        val cryptoSigningKey = cryptoOpsClient.lookup(
            "LOCAL_JWT_SIGNING_KEY",
            0,
            1,
            CryptoKeyOrderBy.CREATED,
            mapOf("alias" to ALIAS, "category" to CATEGORY)
        ).firstOrNull()

        return cryptoSigningKey?.publicKey?.let { keyEncodingService.decodePublicKey(it.array()) }
    }

    private fun base64EncodeJsonMap(input: Map<String, String>): String {
        val jsonInput = JSONObject(input).toString()
        return Base64.getUrlEncoder().encodeToString(jsonInput.toByteArray(StandardCharsets.UTF_8))
    }

    private fun base64DecodeToMap(input: String): Map<String, Any> {
        val decodedClaimString = Base64.getDecoder().decode(input)
        return JSONObject(decodedClaimString).toMap()
    }

    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
    }

    override fun stop() {
    }
}