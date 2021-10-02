package net.corda.httprpc.server.impl.rpcops.impl

import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import net.corda.httprpc.durablestream.DurableStreamHelper
import net.corda.v5.httprpc.api.Controller

class NumberSequencesControllerImpl : Controller {

    override fun register() {
        path("numberseq") {
            post("/retrieve", ::retrieve)
        }
    }

    private fun retrieve(ctx: Context) {
        val cursor = DurableStreamHelper.withDurableStreamContext {
            val pad = when (NumberTypeEnum.valueOf(ctx.queryParam("type")!!)) {
                NumberTypeEnum.EVEN -> 0
                NumberTypeEnum.ODD -> 1
            }

            val longRange: LongRange = (currentPosition + 1)..(currentPosition + maxCount)
            val positionedValues = longRange.map { pos -> pos to (pad + pos * 2) }
            DurableStreamHelper.outcome(Long.MAX_VALUE, false, positionedValues)
        }
        ctx.json(cursor)
    }
}

enum class NumberTypeEnum {
    EVEN, ODD
}