package net.corda.cordapptestutils.internal.tools

import net.corda.cordapptestutils.factories.JsonMarshallingServiceFactory
import net.corda.v5.application.marshalling.JsonMarshallingService

class SimpleJsonMarshallingServiceFactory : JsonMarshallingServiceFactory {
    override fun create(): JsonMarshallingService {
        return SimpleJsonMarshallingService()
    }
}