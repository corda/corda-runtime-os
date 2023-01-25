package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.durablestream.api.FiniteDurableCursorBuilder
import java.time.DayOfWeek

@HttpRestResource(name = "CalendarRestResource", description = "Calendar RPC Ops", path = "calendar")
interface CalendarRestResource : RestResource {

    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)

    @HttpPOST(path = "daysOfTheYear")
    fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarDay>
}