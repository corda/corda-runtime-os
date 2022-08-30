package net.corda.cordapptestutils.factories

import net.corda.v5.application.marshalling.JsonMarshallingService
import java.util.ServiceLoader

interface JsonMarshallingServiceFactory {

    companion object {
        private val delegate by lazy { ServiceLoader.load(JsonMarshallingServiceFactory::class.java).first().create() }
        fun create() : JsonMarshallingService {
            return delegate
        }
    }

    fun create() : JsonMarshallingService
}