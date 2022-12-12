package net.corda.simulator.factories

import net.corda.v5.application.flows.Flow
import net.corda.v5.base.types.MemberX500Name

fun interface ServiceOverrideBuilder<T> {
    fun buildService(member: MemberX500Name, flowClass: Class<out Flow>, service: T?): T
}