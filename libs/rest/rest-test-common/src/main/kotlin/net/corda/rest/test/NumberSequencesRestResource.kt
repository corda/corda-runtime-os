package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.durablestream.api.DurableCursorBuilder

enum class NumberTypeEnum {
    EVEN, ODD
}

@HttpRestResource(
    name = "net.corda.rest.server.impl.rest.resource.NumberSequencesRestResource",
    description = "Number Sequences Rest Resource",
    path = "numberseq"
)
interface NumberSequencesRestResource : RestResource {
    @HttpPOST(path = "retrieve")
    fun retrieve(type: NumberTypeEnum): DurableCursorBuilder<Long>
}