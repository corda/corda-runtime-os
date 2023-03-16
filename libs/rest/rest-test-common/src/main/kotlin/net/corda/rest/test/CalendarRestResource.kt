package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.durablestream.api.FiniteDurableCursorBuilder
import java.time.DayOfWeek

@HttpRestResource(name = "CalendarRestResource", description = "Calendar REST resource", path = "calendar")
interface CalendarRestResource : RestResource {

    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)

    @HttpPOST(path = "daysOfTheYear")
    fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarDay>
}