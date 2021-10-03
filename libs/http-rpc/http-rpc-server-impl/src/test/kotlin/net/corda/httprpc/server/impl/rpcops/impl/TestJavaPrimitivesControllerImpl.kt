package net.corda.httprpc.server.impl.rpcops.impl

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import net.corda.v5.httprpc.api.Controller
import java.lang.NumberFormatException

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
        val number = try {
            -Integer.valueOf(ctx.bodyAsClass(JsonNode::class.java)["number"].asText())
        } catch(e: NumberFormatException) {
            throw BadRequestResponse("Numeric value (3147483647) out of range of int (-2147483648 - 2147483647)")
        }
        ctx.result(number.toString())
    }

    private fun negatePrimitiveInt(ctx: Context) {
        val number = -ctx.bodyAsClass(JsonNode::class.java)["number"].asInt()
        ctx.result(number.toString())
    }

    private fun negateLong(ctx: Context) {
        val number = -ctx.queryParam("number")!!.toLong()
        ctx.result(number.toString())
    }

    private fun negateBoolean(ctx: Context) {
        ctx.result((!ctx.queryParam("bool")!!.toBoolean()).toString())
    }

    private fun reverse(ctx: Context) {
        ctx.result(ctx.pathParam("text").reversed())
    }
}