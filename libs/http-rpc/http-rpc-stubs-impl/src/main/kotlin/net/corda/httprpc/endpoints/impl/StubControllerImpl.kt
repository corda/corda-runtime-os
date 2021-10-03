package net.corda.httprpc.endpoints.impl

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonValue
import io.javalin.apibuilder.ApiBuilder
import net.corda.httprpc.Controller
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.ContentType
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiParam
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody
import io.javalin.plugin.openapi.annotations.OpenApiResponse
import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.httprpc.durablestream.DurableStreamHelper
import net.corda.v5.base.stream.Cursor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar

@Component(service = [Controller::class])
class StubControllerImpl : Controller {

    private companion object {
        private val log = contextLogger()
    }

    override fun register() {
        path("stubs") {
            get(::all)
            post(::post)
//            path("{id}") {
//                get(::get)
//                put(::put)
//                delete(::delete)
//            }
            // this is the javalin 3 syntax, which will change when we move to v4
                get("/:id", ::get)
                put("/:id", ::put)
                delete("/:id", ::delete)
        }
    }

    private fun paths() {

    }

    private fun all(ctx: Context) {
        println(ctx)
        log.info("ALL")
    }

    @OpenApi(
        summary = "Get some json with the passed in id",
        pathParams = [OpenApiParam(name = "id", required = true, type = String::class, description = "The id to get some json with")],
        operationId = "get",
        tags = ["Stub API"],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(SomeJson::class, type = ContentType.JSON)]),
            OpenApiResponse(status = "404"),
            OpenApiResponse(status = "500")
        ]
    )
    private fun get(ctx: Context) {
        val id = ctx.pathParam("id")
        log.info("GET - id")
        ctx.status(200)
        ctx.json(SomeJson(id, 2))
    }

    @OpenApi(
        summary = "Save some json",
        requestBody = OpenApiRequestBody(content = [OpenApiContent(SomeJson::class, type = ContentType.JSON)], description = "The json to save"),
        operationId = "post",
        tags = ["Stub API"],
        responses = [
            OpenApiResponse(status = "201", content = [OpenApiContent(SomeJson::class, type = ContentType.JSON)]),
            OpenApiResponse(status = "500")
        ]
    )
    private fun post(ctx: Context) {
        println(ctx)
        val json = ctx.bodyAsClass(SomeJson::class.java)
        log.info("POST - $json")
        ctx.status(201)
        ctx.json(json)
    }

    private fun put(ctx: Context) {
        val id = ctx.pathParam("id")
        log.info("PUT - $id")
        throw IllegalStateException("This is an exception and I am breaking")
    }

    private fun delete(ctx: Context) {
        val id = ctx.pathParam("id")
        log.info("DELETE - $id")
        ctx.result(id)
    }

    class SomeJson(val a: String, val b: Int)
}

@Component(service = [Controller::class])
class CalendarControllerImpl : Controller {

    override fun register() {
        path("/calendar") {
            post("/daysoftheyear", ::daysOfTheYear)
        }
    }

    @OpenApi(
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(from = CalendarDaysOfTheYearRequest::class, type = "application/json")]
        ),
        responses = [
            OpenApiResponse(
                content = [OpenApiContent(from = CalendarDaysOfTheYearPollResultResponse::class, type = "application/json")],
                status = "200"
            )
        ]
    )
    private fun daysOfTheYear(ctx: Context) {
        val json = ctx.bodyAsClass(CalendarDaysOfTheYearRequest::class.java)
        val year = json.year
        val pollResult = DurableStreamHelper.withDurableStreamContext(json.context) {

            val calendar = GregorianCalendar().apply {
                set(Calendar.YEAR, year)
                set(Calendar.HOUR, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysPerYear = if (calendar.isLeapYear(year)) 366L else 365L

            val longRange = (currentPosition + 1)..(currentPosition + maxCount).coerceAtMost(daysPerYear - 1)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val positionedValues = longRange.map { pos ->
                val dayOfYear = pos.toInt() + 1
                calendar.set(Calendar.DAY_OF_YEAR, dayOfYear)
                val zdt = calendar.toZonedDateTime()
                val dayOfWeek = DayOfWeek.from(zdt)
                val dateAsString = formatter.format(zdt)
                pos to CalendarDay(dayOfWeek, dateAsString)
            }

            val remainingElementsCountEstimate = daysPerYear - longRange.last - 1
            DurableStreamHelper.outcome(remainingElementsCountEstimate, remainingElementsCountEstimate == 0L, positionedValues)
        }
        ctx.json(pollResult)
    }

    data class CalendarDaysOfTheYearRequest(val year: Int, val context: DurableStreamContext)

    // Having to manually define the response compared to the generated version we had is a downside
    // However, it is simple to create this mapping ourselves using the annotations
    data class CalendarDaysOfTheYearPollResultResponse(
        val positionedValues: List<Cursor.PollResult.PositionedValue<CalendarDay>>,
        val remainingElementsCountEstimate: Long?,
        val isLastResult: Boolean
    )

    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)
}