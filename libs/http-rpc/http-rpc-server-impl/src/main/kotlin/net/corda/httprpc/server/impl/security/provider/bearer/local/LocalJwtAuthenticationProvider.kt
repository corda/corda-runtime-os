package net.corda.httprpc.server.impl.security.provider.bearer.local

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.common.json.serialization.jacksonObjectMapper
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.security.provider.bearer.BearerTokenAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.security.local.HttpRpcLocalJwtSigner
import net.corda.v5.base.util.contextLogger
import java.util.*
import javax.security.auth.login.FailedLoginException

internal class LocalJwtAuthenticationProvider(
    private val jwtProcessor: HttpRpcLocalJwtSigner,
    private val rpcSecurityManager: RPCSecurityManager
) : BearerTokenAuthenticationProvider() {

    companion object {
        private val logger = contextLogger()
    }

    override fun doAuthenticate(credential: BearerTokenAuthenticationCredentials): AuthorizingSubject {

        try {
            if (jwtProcessor.verify(credential.token)) {
                val decodedClaims = String(Base64.getDecoder().decode(credential.token.split(".")[1]))
                val subject = extractSubFromJwt(decodedClaims)
                return rpcSecurityManager.buildSubject(subject)
            } else {
                throw FailedLoginException("Failed to parse JWT token.")
            }
        } catch (e: Exception) {
            logger.error("Unexpected exception when parsing token", e)
            // Catching Exception here to mitigate https://nvd.nist.gov/vuln/detail/CVE-2021-27568,
            // even though: `com.nimbusds.jose.util.JSONObjectUtils.parse` already has similar sort of logic,
            // but this may change in the future
            // versions.
            throw FailedLoginException("Failed to parse JWT token.")
        }
    }

    private fun extractSubFromJwt(claims: String): String {
        val claimsMap: Map<String, String> = jacksonObjectMapper().readValue(claims)
        if (claimsMap.isEmpty()) {
            throw Exception("Claims unable to be parsed or missing")
        }
        val sub = claimsMap["sub"]
        if (!sub.isNullOrBlank())
            return sub
        else throw Exception("some error")
    }
}