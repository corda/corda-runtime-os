package net.corda.components.crypto.rpc

interface CryptoRpcHandler<TCTX, TREQ> {
    fun handle(context: TCTX, request: TREQ): Any?
}