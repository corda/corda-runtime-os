package net.corda.simulator.factories

import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.DoNotImplement
import java.util.ServiceLoader

/**
 * Factory for building a [JsonMarshallingService].
 */
@DoNotImplement
interface JsonMarshallingServiceFactory {

    companion object {
        private val delegate by lazy { ServiceLoader.load(JsonMarshallingServiceFactory::class.java).first().create() }

        /**
         * Creates a standalone service, which can be used outside of Simulator for
         * parsing and formatting of arbitrary objects into Json.
         *
         * @return a [JsonMarshallingService]
         */
        fun create() : JsonMarshallingService {
            return delegate
        }
    }

    /**
     * @return a [JsonMarshallingService]
     */
    fun create() : JsonMarshallingService
}