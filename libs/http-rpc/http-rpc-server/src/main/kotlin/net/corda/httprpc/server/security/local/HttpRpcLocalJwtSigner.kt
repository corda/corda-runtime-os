package net.corda.httprpc.server.security.local

interface HttpRpcLocalJwtSigner {

    fun buildAndSignJwt(claims: Map<String, String>): String

    fun verify(token: String): Boolean
}