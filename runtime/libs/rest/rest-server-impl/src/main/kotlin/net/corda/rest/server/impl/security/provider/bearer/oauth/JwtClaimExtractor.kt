package net.corda.rest.server.impl.security.provider.bearer.oauth

import com.nimbusds.jwt.JWTClaimsSet

internal interface JwtClaimExtractor {
    fun getUsername(claimsSet: JWTClaimsSet): String
}
