package net.corda.httprpc.server.impl.security.provider.bearer.oauth

import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTParser
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.security.provider.bearer.BearerTokenAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.v5.base.util.contextLogger
import java.util.function.Supplier
import javax.security.auth.login.FailedLoginException

internal open class JwtAuthenticationProvider(
    private val jwtProcessor: JwtProcessor,
    private val claimExtractor: JwtClaimExtractor,
    private val rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>
) : BearerTokenAuthenticationProvider() {

    companion object {
        private val logger = contextLogger()
    }

    override fun doAuthenticate(credential: BearerTokenAuthenticationCredentials): AuthorizingSubject {
        val jwt = try {
            JWTParser.parse(credential.token)
        } catch (e: Exception) {
            logger.error("Unexpected exception when parsing token", e)
            // Catching Exception here to mitigate https://nvd.nist.gov/vuln/detail/CVE-2021-27568,
            // even though: `com.nimbusds.jose.util.JSONObjectUtils.parse` already has similar sort of logic,
            // but this may change in the future
            // versions.
            throw FailedLoginException("Failed to parse JWT token.")
        }

        return extractSubjectFromJwt(jwt)
    }

    protected open fun extractSubjectFromJwt(jwt: JWT): AuthorizingSubject {
        return try {
            val claims = jwtProcessor.process(jwt)
            val username = claimExtractor.getUsername(claims)
            rpcSecurityManagerSupplier.get().buildSubject(username)
        } catch (e: BadJOSEException) {
            throw FailedLoginException("Unable to validate token.")
        }
    }
}
