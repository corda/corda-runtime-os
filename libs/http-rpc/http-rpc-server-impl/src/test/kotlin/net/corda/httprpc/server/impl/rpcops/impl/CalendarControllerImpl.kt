package net.corda.httprpc.server.impl.rpcops.impl

import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody
import io.javalin.plugin.openapi.annotations.OpenApiResponse
import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.httprpc.durablestream.DurableStreamHelper
import net.corda.v5.base.stream.Cursor
import net.corda.v5.httprpc.api.Controller
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar

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