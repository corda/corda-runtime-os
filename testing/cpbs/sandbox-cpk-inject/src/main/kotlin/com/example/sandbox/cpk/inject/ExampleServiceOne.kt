package com.example.sandbox.cpk.inject

import com.example.sandbox.cpk.inject.impl.ExampleServiceTwo
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.services.CordaService

class ExampleServiceOne : CordaService, CordaFlowInjectable {
    @CordaInject
    private lateinit var service: ExampleServiceTwo

    fun apply(data: String): String {
        return service.apply(data)
    }
}
