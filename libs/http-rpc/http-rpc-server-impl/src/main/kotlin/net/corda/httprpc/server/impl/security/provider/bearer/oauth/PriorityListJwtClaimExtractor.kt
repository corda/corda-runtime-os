package net.corda.httprpc.server.impl.security.provider.bearer.oauth

import com.nimbusds.jwt.JWTClaimsSet
import net.corda.httprpc.exception.NotAuthenticatedException

internal class PriorityListJwtClaimExtractor(private val claimList: List<String>) : JwtClaimExtractor {
    override fun getUsername(claimsSet: JWTClaimsSet): String {
        return claimList.map {
            claimsSet.getStringClaim(it)
        }.firstOrNull { it != null } ?: throw NotAuthenticatedException("Unable to extract principal name from token.")
    }
}
