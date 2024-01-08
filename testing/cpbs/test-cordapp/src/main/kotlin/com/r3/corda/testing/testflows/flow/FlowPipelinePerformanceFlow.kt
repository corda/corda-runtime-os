package com.r3.corda.testing.testflows.flow

import com.r3.corda.testing.testflows.messages.FlowPerformanceTestInput
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.PublicKey

class FlowPipelinePerformanceFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var signingService: SigningService

    private val publicKey: PublicKey by lazy {
        generatePublicKey()
    }

    private val mockSignatureSpec by lazy {
        MockSignatureSpec()
    }

    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting FlowPipelinePerformanceFlow...")
        val input = requestBody
            .getRequestBodyAs(jsonMarshallingService, FlowPerformanceTestInput::class.java)

        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)

        log.info("Processing ${input.eventIterations} signing events.")

        for (i in 1..input.eventIterations) {
            signingService.sign(bytesToSign, publicKey, mockSignatureSpec)

            if (i % 10 == 0) {
                log.info("Progress: $i out of ${input.eventIterations} signing events processed.")
            }
        }

        log.info("All ${input.eventIterations} signing events processed successfully.")

        return jsonMarshallingService.format("FlowPipelinePerformanceFlow completed successfully")
    }

    private fun generatePublicKey(): PublicKey {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair().public
    }

    private inner class MockSignatureSpec : SignatureSpec {
        override fun getSignatureName(): String =
            "FlowEnginePerformanceTest-MockSignature"
    }
}
