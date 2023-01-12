package com.example.cpk

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.parse
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash

@Suppress("unused")
class ExampleFlow : RestStartableFlow {
    private val logger = loggerFor<ExampleFlow>()

    @CordaInject
    private lateinit var jsonMarshaller: JsonMarshallingService

    @CordaInject
    private lateinit var digestService: DigestService

    init {
        logger.info("Created")
    }

    private fun hashOf(bytes: ByteArray): SecureHash {
        return digestService.hash(bytes, DigestAlgorithmName("SHA-256-TRIPLE"))
    }

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        val json = requestBody.getRequestBody()
        logger.info("Invoked: JSON={}", json)
        val input = jsonMarshaller.parse<FlowInput>(json)
        return hashOf(
            bytes = input.message?.toByteArray() ?: byteArrayOf()
        ).also { result ->
            logger.info("Result={}", result)
        }.toHexString()
    }
}
