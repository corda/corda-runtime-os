package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.durablestream.DurableStreamHelper
import net.corda.v5.base.stream.FiniteDurableCursorBuilder
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar

@Suppress("MagicNumber")
class CalendarRPCOpsImpl : CalendarRPCOps, PluggableRPCOps<CalendarRPCOps> {

    override val targetInterface: Class<CalendarRPCOps>
        get() = CalendarRPCOps::class.java

    override val protocolVersion = 1000

    override fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarRPCOps.CalendarDay> {
        return DurableStreamHelper.withDurableStreamContext {

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
                pos to CalendarRPCOps.CalendarDay(dayOfWeek, dateAsString)
            }

            val remainingElementsCountEstimate = daysPerYear - longRange.last - 1
            DurableStreamHelper.outcome(remainingElementsCountEstimate, remainingElementsCountEstimate == 0L, positionedValues)
        }
    }
}