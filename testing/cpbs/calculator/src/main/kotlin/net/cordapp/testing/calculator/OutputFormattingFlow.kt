package net.cordapp.testing.calculator

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.slf4j.LoggerFactory

class OutputFormattingFlow(private val result: Int) : SubFlow<String> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
