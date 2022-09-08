package net.corda.simulator.runtime.tools

import net.corda.simulator.factories.JsonMarshallingServiceFactory
import net.corda.v5.application.marshalling.JsonMarshallingService

class SimpleJsonMarshallingServiceFactory : JsonMarshallingServiceFactory {
    override fun create(): JsonMarshallingService {
        return SimpleJsonMarshallingService()
    }
}