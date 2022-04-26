package net.corda.httprpc.server.security.local

interface HttpRpcLocalJwtSigner {

    fun buildAndSignJwt(payload: String): String

    fun verify(token: String): Boolean
}