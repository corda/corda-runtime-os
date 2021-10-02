package net.corda.httprpc.server.impl.rpcops.impl

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import net.corda.v5.httprpc.api.Controller
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.UUID

class TestHealthCheckControllerImpl : Controller {

    override fun register() {
        path("health") {
            get("/sanity", ::void)
            get("/void", ::voidResponse)
            get("/hello/:name", ::hello)
            get("/hello2/:name", ::hello2)
            post("/ping", ::ping)
            get("/plusone/:numbers", ::plusOne)
            post("/plusone", ::plus)
            post("/plusdouble", ::plusDouble)
            post("/bodyplayground", ::bodyPlayground)
            post("/timecall", ::timeCall)
            post("/datecall", ::dateCall)
            post("/instantcall", ::instantCall)
            get("/throwexception", ::throwException)
            get("/lateraddedcall", ::laterAddedCall)
            get("/firstdaysoftheyear", ::firstDaysOfTheYear)
            post("/parseuuid", ::parseUuid)
        }
    }

    private fun void(ctx: Context) {
        ctx.result("Sane")
    }

    private fun voidResponse(ctx: Context) {
        println(ctx.body())
    }

    private fun hello(ctx: Context) {
        ctx.result("Hello pathParam : ${ctx.pathParam("pathParam")}")
    }

    private fun hello2(ctx: Context) {
        ctx.result("Hello queryParam: ${ctx.queryParam("queryParam")}, pathParam : ${ctx.pathParam("pathParam")}")
    }

    private fun ping(ctx: Context) {
        ctx.result("Pong for ${ctx.bodyAsClass(PingPongData::class.java).str}")
    }

    private fun plusOne(ctx: Context) {
        ctx.result(ctx.queryParam("numbers")!!.split(",").map { it.toDouble() + 1 }.toString())
    }

    private fun plus(ctx: Context) {
        ctx.result((ctx.queryParam("number")!!.toInt() + 1).toString())
    }

    private fun plusDouble(ctx: Context) {
        ctx.result((ctx.queryParam("number")!!.toDouble() + 1).toString())
    }

    private fun bodyPlayground(ctx: Context) {
        println(ctx.body())
    }

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