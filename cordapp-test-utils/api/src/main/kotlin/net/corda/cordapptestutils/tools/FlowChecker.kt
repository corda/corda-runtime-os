package net.corda.cordapptestutils.tools

import net.corda.cordapptestutils.exceptions.ServiceConfigurationException
import net.corda.v5.application.flows.Flow
import java.util.ServiceLoader

interface FlowChecker {
    fun check(flowClass: Class<out Flow>)

    companion object {
        private val delegate = ServiceLoader.load(FlowChecker::class.java).first() ?:
            throw ServiceConfigurationException(FlowChecker::class.java)
        fun check(flowClass: Class<out Flow>) {
            delegate.check(flowClass)
        }
    }
}