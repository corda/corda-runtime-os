package net.corda.httprpc.server.impl.security.provider.bearer.local

import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTParser
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.bearer.BearerTokenAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.JwtAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.JwtProcessor
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.PriorityListJwtClaimExtractor
import net.corda.httprpc.server.impl.security.provider.credentials.AuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.security.local.HttpRpcLocalJwtSigner
import net.corda.v5.base.util.contextLogger
import org.bouncycastle.util.encoders.Base64Encoder
import java.util.*
import javax.security.auth.login.FailedLoginException

internal class LocalJwtAuthenticationProvider(
    val jwtProcessor: HttpRpcLocalJwtSigner,
    val rpcSecurityManager: RPCSecurityManager
) : BearerTokenAuthenticationProvider() {

    companion object {
        private val logger = contextLogger()
    }

    override fun doAuthenticate(credential: BearerTokenAuthenticationCredentials): AuthorizingSubject {

        val jwt = try {
            if(jwtProcessor.verify(credential.token)){

                val decoded = String(Base64.getDecoder().decode(credential.token.split(".")[1]))

                rpcSecurityManager.buildSubject(decoded)
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

}