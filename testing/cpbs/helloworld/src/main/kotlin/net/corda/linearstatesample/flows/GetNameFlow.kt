package net.corda.linearstatesample.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.util.contextLogger

class GetNameFlow(private val json: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    override fun call(): String {
        try {
            return jsonMarshallingService.parseJson<ExampleMessage>(json).who!!
        } catch (e: Throwable) {
            log.warn("could not deserialize '$json' because:'${e.message}'")
        }

        return ""
    }
}