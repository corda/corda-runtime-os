package net.corda.httprpc.server.impl.rpcops.impl

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody
import net.corda.httprpc.Controller
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.UUID

class TestHealthCheckControllerImpl : Controller {

    override fun register() {
        path("/health") {
            get("/sanity", ::void)
            get("/void", ::voidResponse)
            get("/hello/:name", ::hello)
            get("/hello2/:name", ::hello2)
            post("/ping", ::ping)
            post("/plusone/:number", ::plusOne)
            get("/plusone", ::plus)
            post("/plusdouble", ::plusDouble)
            post("/bodyplayground", ::bodyPlayground)
            post("/bodyplayground2", ::bodyPlayground2)
            post("/timecall", ::timeCall)
            post("/datecall", ::dateCall)
            post("/instantcall", ::instantCall)
            get("/throwexception", ::throwException)
            get("/lateraddedcall", ::laterAddedCall)
            get("/firstdaysoftheyear", ::firstDaysOfTheYear)
            post("/parseuuid", ::parseUuid)
            get("/getprotocolversion", ::protocolVersion)
        }
    }

    private fun void(ctx: Context) {
        ctx.result("Sane")
    }

    private fun voidResponse(ctx: Context) {
        println(ctx.body())
    }

    private fun hello(ctx: Context) {
        ctx.result("Hello ${ctx.queryParam("id")} : ${ctx.pathParam("name")}")
    }

    private fun hello2(ctx: Context) {
        ctx.result("Hello queryParam: ${ctx.queryParam("id")}, pathParam : ${ctx.pathParam("name")}")
    }

    private fun ping(ctx: Context) {
        if (ctx.body().isNotBlank()) {
            ctx.result("Pong for ${ctx.bodyAsClass(PingPongData::class.java).str}")
        } else {
            ctx.result("Pong for ")
        }
    }

    private fun plusOne(ctx: Context) {
        ctx.result((ctx.pathParam("number").toLong() + 1).toString())
    }

    private fun plus(ctx: Context) {
        ctx.result(ctx.queryParams("numbers").map { it.toDouble() + 1 }.toString())
    }

    private fun plusDouble(ctx: Context) {
        ctx.result((ctx.bodyAsClass(ObjectNode::class.java)["number"].asDouble() + 1).toString())
    }

    private fun bodyPlayground(ctx: Context) {
        val json = ctx.bodyAsClass(JsonNode::class.java)
        val s1 = json["s1"]?.asText()
        val s2 = json["s2"]?.asText()
        ctx.result("$s1 $s2")
    }

    private fun bodyPlayground2(ctx: Context) {
        val json = ctx.bodyAsClass(JsonNode::class.java)
        val s1 = json["s1"]?.asText() ?: throw BadRequestResponse("s1 must be defined")
        val s2 = json["s2"]?.asText()
        ctx.result("$s1 $s2")
    }

    @OpenApi(requestBody = OpenApiRequestBody(content = [OpenApiContent(from = TimeCallDto::class, type = "application/json")]))
    private fun timeCall(ctx: Context) {
        ctx.result(ctx.bodyAsClass(TimeCallDto::class.java).time.toString())
    }

    private fun dateCall(ctx: Context) {
        ctx.result(ctx.bodyAsClass(DateCallDto::class.java).date.toString())
    }

    private fun instantCall(ctx: Context) {
        ctx.result(ctx.bodyAsClass(InstantCallDto::class.java).instant.toString())
    }

    private fun throwException(ctx: Context) {
        val exception = ctx.queryParam("exception")
        throw Class.forName(exception)
            .getDeclaredConstructor(String::class.java)
            .newInstance("$exception thrown from throwException method") as Throwable
    }

    private fun laterAddedCall(ctx: Context) {
        println(ctx.body())
    }

    private fun firstDaysOfTheYear(ctx: Context) {
        val calendar = GregorianCalendar().apply {
            set(Calendar.YEAR, ctx.queryParam("year")!!.toInt())
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val results = (1..ctx.queryParam("daysCount")!!.toInt()).map { DateCallDto(calendar.apply { set(Calendar.DAY_OF_YEAR, it) }.time) }
        ctx.json(results)
    }

    private fun parseUuid(ctx: Context) {
        ctx.json(UUID.fromString(ctx.pathParam("uuid")))
    }

    private fun protocolVersion(ctx: Context) {
        ctx.result("2")
    }

    data class TimeCallDto(val time: ZonedDateTime)

    data class DateCallDto(val date: Date)

    data class InstantCallDto(val instant: Instant)

    class PingPongData(@JsonDeserialize(using = PingPongDataDeserializer::class) val str: String)
    class PingPongDataDeserializer : JsonDeserializer<String>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String {
            return "str = ${p.text}"
        }
    }
}