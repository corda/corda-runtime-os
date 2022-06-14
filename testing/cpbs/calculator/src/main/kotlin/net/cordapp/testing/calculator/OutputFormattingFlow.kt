package net.cordapp.testing.calculator

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Subflow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.util.contextLogger

class OutputFormattingFlow(private val result: Int) : Subflow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    override fun call(): String {
        try {
            return jsonMarshallingService.format(OutputMessage(result))
        } catch (e: Exception) {
            log.warn("could not serialise result '$result'")
            throw e
        }
    }
}
