package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.durablestream.api.FiniteDurableCursorBuilder
import java.time.DayOfWeek

@HttpRpcResource(name = "CalendarRestResource", description = "Calendar RPC Ops", path = "calendar")
interface CalendarRestResource : RestResource {

    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)

    @HttpRpcPOST(path = "daysOfTheYear")
    fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarDay>
}