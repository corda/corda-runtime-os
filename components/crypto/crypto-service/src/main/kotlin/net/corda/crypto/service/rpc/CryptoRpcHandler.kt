package net.corda.crypto.service.rpc

interface CryptoRpcHandler<CTX, REQUEST> {
    fun handle(context: CTX, request: REQUEST): Any
}