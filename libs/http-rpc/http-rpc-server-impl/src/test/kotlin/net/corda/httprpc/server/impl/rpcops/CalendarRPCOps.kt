package net.corda.httprpc.server.impl.rpcops

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.stream.FiniteDurableCursorBuilder
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import java.time.DayOfWeek

@HttpRpcResource(name = "net.corda.extensions.node.rpc.CalendarRPCOps", description = "Calendar RPC Ops", path = "calendar")
interface CalendarRPCOps : RpcOps {

    @CordaSerializable
    data class CalendarDay(val dayOfWeek: DayOfWeek, val dayOfYear: String)

    @HttpRpcPOST
    fun daysOfTheYear(year: Int): FiniteDurableCursorBuilder<CalendarDay>
}