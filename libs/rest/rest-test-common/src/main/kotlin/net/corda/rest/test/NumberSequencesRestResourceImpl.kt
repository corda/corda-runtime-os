package net.corda.rest.test

import net.corda.rest.PluggableRestResource
import net.corda.rest.durablestream.DurableStreamHelper
import net.corda.rest.durablestream.api.DurableCursorBuilder

@Suppress("MagicNumber")
class NumberSequencesRestResourceImpl : NumberSequencesRestResource, PluggableRestResource<NumberSequencesRestResource> {

    override val targetInterface: Class<NumberSequencesRestResource>
        get() = NumberSequencesRestResource::class.java

    override val protocolVersion = 1000

    override fun retrieve(type: NumberTypeEnum): DurableCursorBuilder<Long> {
        return DurableStreamHelper.withDurableStreamContext {
            val pad = when (type) {
                NumberTypeEnum.EVEN -> 0
                NumberTypeEnum.ODD -> 1
            }

            val longRange: LongRange = (currentPosition + 1)..(currentPosition + maxCount)
            val positionedValues = longRange.map { pos -> pos to (pad + pos * 2) }
            DurableStreamHelper.outcome(Long.MAX_VALUE, false, positionedValues)
        }
    }
}
