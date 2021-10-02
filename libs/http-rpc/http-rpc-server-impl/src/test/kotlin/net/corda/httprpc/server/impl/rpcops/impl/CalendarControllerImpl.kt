package net.corda.httprpc.server.impl.rpcops.impl

import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import net.corda.httprpc.durablestream.DurableStreamHelper
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

    private fun daysOfTheYear(ctx: Context) {
        val year = ctx.queryParam("year")!!.toInt()
        val cursor = DurableStreamHelper.withDurableStreamContext {

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

        ctx.json(cursor)
    }

    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)
}