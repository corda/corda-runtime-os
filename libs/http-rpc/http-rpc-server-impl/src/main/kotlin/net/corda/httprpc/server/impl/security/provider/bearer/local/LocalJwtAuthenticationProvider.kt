package net.corda.httprpc.server.impl.security.provider.bearer.local

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.JwtAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.JwtProcessor
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.PriorityListJwtClaimExtractor

internal class LocalJwtAuthenticationProvider(
    jwtProcessor: JwtProcessor,
    rpcSecurityManager: RPCSecurityManager
) : JwtAuthenticationProvider(jwtProcessor, PriorityListJwtClaimExtractor(listOf("")), rpcSecurityManager) {
}