package net.corda.testutils.tools

import net.corda.v5.application.flows.Flow

interface FlowChecker {
    fun check(flowClass: Class<out Flow>)
}