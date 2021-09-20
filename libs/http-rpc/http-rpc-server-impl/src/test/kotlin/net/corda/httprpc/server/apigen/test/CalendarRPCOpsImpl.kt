package net.corda.httprpc.server.apigen.test

import net.corda.v5.base.stream.FiniteDurableCursor
import net.corda.v5.base.stream.FiniteDurableCursorBuilder
import net.corda.v5.base.stream.PositionManager
import net.corda.v5.httprpc.api.PluggableRPCOps
import org.mockito.kotlin.mock

@Suppress("MagicNumber")
class CalendarRPCOpsImpl : CalendarRPCOps, PluggableRPCOps<CalendarRPCOps> {

    override val targetInterface: Class<CalendarRPCOps>
        get() = CalendarRPCOps::class.java

    override val protocolVersion = 1000

    override fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarRPCOps.CalendarDay> {
        // DURABLE STREAM HELPER USES THE RPC CONTEXT WHICH IS IN SERVER
        // THIS CODE NEEDS TO BE REFACTORED SO THAT RPC OPS IMPLS CAN USE WITHOUT DEPENDING ON THE SERVER MODULE
//        return DurableStreamHelper.withDurableStreamContext {
//
//            val calendar = GregorianCalendar().apply {
//                set(Calendar.YEAR, year)
//                set(Calendar.HOUR, 10)
//                set(Calendar.MINUTE, 0)
//                set(Calendar.SECOND, 0)
//                set(Calendar.MILLISECOND, 0)
//            }
//            val daysPerYear = if (calendar.isLeapYear(year)) 366L else 365L
//
//            val longRange = (currentPosition + 1)..(currentPosition + maxCount).coerceAtMost(daysPerYear - 1)
//            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
//            val positionedValues = longRange.map { pos ->
//                val dayOfYear = pos.toInt() + 1
//                calendar.set(Calendar.DAY_OF_YEAR, dayOfYear)
//                val zdt = calendar.toZonedDateTime()
//                val dayOfWeek = DayOfWeek.from(zdt)
//                val dateAsString = formatter.format(zdt)
//                pos to CalendarRPCOps.CalendarDay(dayOfWeek, dateAsString)
//            }
//
//            val remainingElementsCountEstimate = daysPerYear - longRange.last - 1
//            DurableStreamHelper.outcome(remainingElementsCountEstimate, remainingElementsCountEstimate == 0L, positionedValues)
//        }
        return object : FiniteDurableCursorBuilder<CalendarRPCOps.CalendarDay> {
            override var positionManager: PositionManager = mock()

            override fun build(): FiniteDurableCursor<CalendarRPCOps.CalendarDay> {
                return mock()
            }
        }
    }
}