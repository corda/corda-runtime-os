package com.example.cpk

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.application.serialization.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash

@Suppress("unused")
class ExampleFlow : RPCStartableFlow<Unit> {
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
    override fun call(requestBody: String): String {
        logger.info("Invoked: JSON=$requestBody")
        val input = jsonMarshaller.parseJson<FlowInput>(requestBody)
        return hashOf(
            bytes = input.message?.toByteArray() ?: byteArrayOf()
        ).also { result ->
            logger.info("Result=$result")
        }.toHexString()
    }
}
