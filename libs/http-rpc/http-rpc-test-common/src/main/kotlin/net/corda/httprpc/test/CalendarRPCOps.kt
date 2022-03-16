package net.corda.httprpc.test

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.v5.base.stream.FiniteDurableCursorBuilder
import java.time.DayOfWeek

@HttpRpcResource(name = "CalendarRPCOps", description = "Calendar RPC Ops", path = "calendar")
interface CalendarRPCOps : RpcOps {

    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)

    @HttpRpcPOST(path = "daysOfTheYear")
    fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarDay>
}