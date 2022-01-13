package net.corda.crypto.service.rpc

import net.corda.data.crypto.wire.CryptoRequestContext

interface CryptoRpcHandler<REQUEST> {
    fun handle(context: CryptoRequestContext, request: REQUEST): Any
}