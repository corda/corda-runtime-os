package net.corda.httprpc.server.security.provider.bearer.oauth

import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet

internal interface JwtProcessor {
    fun process(jwt: JWT): JWTClaimsSet
}
