package net.corda.httprpc.server.impl.rpcops.impl

import net.corda.httprpc.durablestream.DurableStreamHelper
import net.corda.httprpc.server.impl.rpcops.NumberSequencesRPCOps
import net.corda.httprpc.server.impl.rpcops.NumberTypeEnum
import net.corda.v5.base.stream.DurableCursorBuilder
import net.corda.httprpc.PluggableRPCOps

@Suppress("MagicNumber")
class NumberSequencesRPCOpsImpl : NumberSequencesRPCOps, PluggableRPCOps<NumberSequencesRPCOps> {

    override val targetInterface: Class<NumberSequencesRPCOps>
        get() = NumberSequencesRPCOps::class.java


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