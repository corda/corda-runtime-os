package net.corda.httprpc.server.security.provider.bearer.oauth

import com.nimbusds.jwt.JWTClaimsSet

internal interface JwtClaimExtractor {
    fun getUsername(claimsSet: JWTClaimsSet) : String
}
