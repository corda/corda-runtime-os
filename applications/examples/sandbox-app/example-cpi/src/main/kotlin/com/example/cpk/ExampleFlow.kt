package com.example.cpk

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash

@Suppress("unused")
@InitiatingFlow
@StartableByRPC
class ExampleFlow(private val json: String) : Flow<SecureHash> {
    private val logger = loggerFor<ExampleFlow>()

    @CordaInject
    private lateinit var jsonMarshaller: JsonMarshallingService

    @CordaInject
    private lateinit var customCrypto: CustomCrypto

    init {
        logger.info("Created")
    }

    @Suspendable
    override fun call(): SecureHash {
        logger.info("Invoked: JSON=$json")
        val input = jsonMarshaller.parseJson<FlowInput>(json)
        return customCrypto.hashOf(
            bytes = input.message?.toByteArray() ?: byteArrayOf()
        ).also { result ->
            logger.info("Result=$result")
        }
    }
}
