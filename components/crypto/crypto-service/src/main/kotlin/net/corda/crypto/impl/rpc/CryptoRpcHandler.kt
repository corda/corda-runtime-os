package net.corda.crypto.impl.rpc

interface CryptoRpcHandler<TCTX, TREQ> {
    fun handle(context: TCTX, request: TREQ): Any?
}