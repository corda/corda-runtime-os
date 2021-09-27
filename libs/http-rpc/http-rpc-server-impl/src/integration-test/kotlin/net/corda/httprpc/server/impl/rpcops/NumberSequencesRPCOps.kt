package net.corda.httprpc.server.impl.rpcops

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.stream.DurableCursorBuilder
import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

@CordaSerializable
enum class NumberTypeEnum {
    EVEN, ODD
}

@HttpRpcResource(
        name = "net.corda.httprpc.server.impl.rpcops.NumberSequencesRPCOps",
        description = "Number Sequences RPC Ops",
        path = "numberseq"
)
interface NumberSequencesRPCOps : RpcOps {
    @HttpRpcPOST
    fun retrieve(type: NumberTypeEnum): DurableCursorBuilder<Long>
}