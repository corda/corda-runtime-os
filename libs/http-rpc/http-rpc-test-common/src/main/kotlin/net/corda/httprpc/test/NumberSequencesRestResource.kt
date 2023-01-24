package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.durablestream.api.DurableCursorBuilder

enum class NumberTypeEnum {
    EVEN, ODD
}

@HttpRpcResource(
    name = "net.corda.httprpc.server.impl.rpcops.NumberSequencesRestResource",
    description = "Number Sequences RPC Ops",
    path = "numberseq"
)
interface NumberSequencesRestResource : RestResource {
    @HttpRpcPOST(path = "retrieve")
    fun retrieve(type: NumberTypeEnum): DurableCursorBuilder<Long>
}