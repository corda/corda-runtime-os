package net.corda.testing.calculator

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.base.util.contextLogger

class OutputFormattingFlow(private val result: Int) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    override fun call(): String {
        try {
            return jsonMarshallingService.formatJson(OutputMessage(result))
        } catch (e: Exception) {
            log.warn("could not serialise result '$result'")
            throw e
        }
    }
}
