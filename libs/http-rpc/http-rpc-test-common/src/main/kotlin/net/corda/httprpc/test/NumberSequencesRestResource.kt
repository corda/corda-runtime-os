package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.durablestream.api.DurableCursorBuilder

enum class NumberTypeEnum {
    EVEN, ODD
}

@HttpRestResource(
    name = "net.corda.httprpc.server.impl.rpcops.NumberSequencesRestResource",
    description = "Number Sequences Rest Resource",
    path = "numberseq"
)
interface NumberSequencesRestResource : RestResource {
    @HttpPOST(path = "retrieve")
    fun retrieve(type: NumberTypeEnum): DurableCursorBuilder<Long>
}