package net.corda.httprpc.server.impl.rpcops.impl

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import net.corda.v5.httprpc.api.Controller

class TestJavaPrimitivesControllerImpl : Controller {

    override fun register() {
        path("/java") {
            post("/negateinteger", ::negateInt)
            post("/negateprimitiveinteger", ::negatePrimitiveInt)
            get("/negate_long", ::negateLong)
            get("/negate_boolean", ::negateBoolean)
            get("/reverse/:text", ::reverse)
        }
    }

    private fun negateInt(ctx: Context) {
        ctx.result(ctx.body().toInt().inv().toString())
    }

    private fun negatePrimitiveInt(ctx: Context) {
        ctx.result(ctx.body().toInt().inv().toString())
    }

    private fun negateLong(ctx: Context) {
        ctx.result(ctx.queryParam("number")!!.toLong().inv().toString())
    }

    private fun negateBoolean(ctx: Context) {
        ctx.result((!ctx.queryParam("bool")!!.toBoolean()).toString())
    }

    private fun reverse(ctx: Context) {
        ctx.result(ctx.pathParam("text").reversed())
    }
}