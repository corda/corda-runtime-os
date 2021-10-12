package net.corda.crypto.service.rpc

interface CryptoRpcHandler<TCTX, TREQ> {
    fun handle(context: TCTX, request: TREQ): Any?
}