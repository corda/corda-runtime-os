package com.r3.corda.testing.smoketests.flow

import com.r3.corda.testing.smoketests.flow.messages.RestSmokeTestInput
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.ExternalMessaging
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.PublicKey

@Suppress("unused", "TooManyFunctions")
@InitiatingFlow(protocol = "smoke-test-protocol")
class RestSmokeTestFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var digitalSignatureVerificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var signatureSpecService: SignatureSpecService

    @CordaInject
    lateinit var compositeKeyGenerator: CompositeKeyGenerator

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var externalMessaging: ExternalMessaging

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, RestSmokeTestInput::class.java)

        val x500Name = request.getValue("memberX500")
        log.info("Called for $x500Name")
//        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        val eventIterations = 10

        log.info("Processing $eventIterations signing events.")

//        for (i in 1..eventIterations) {
//            signingService.sign(bytesToSign, mockPublicKey, mockSignatureSpec)
//
////            if (i % 10 == 0) {
//            log.info("Progress: $i out of $eventIterations signing events processed.")
////            }
//        }

        log.info("All $eventIterations signing events processed successfully.")

        return true.toString()
    }

    private class SigningResult(
        val publicKey: PublicKey,
        val bytesToSign: ByteArray,
        val signature: DigitalSignature,
        val signatureSpec: SignatureSpec
    )

    @Suspendable
    private fun RestSmokeTestInput.performSigning() : SigningResult {
        val x500Name = getValue("memberX500")
        val member = memberLookup.lookup(MemberX500Name.parse(x500Name))
        checkNotNull(member) { "Member $x500Name could not be looked up" }
        val publicKey = member.ledgerKeys[0]
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        log.info("Crypto - Signing bytes $bytesToSign with public key '$publicKey'")
        val signatureSpec =
            signatureSpecService.defaultSignatureSpec(publicKey)
                ?: throw IllegalStateException("Default signature spec not found for key")
        val signature = signingService.sign(bytesToSign, publicKey, signatureSpec)
        log.info("Crypto - Signature $signature received")
        return SigningResult(publicKey, bytesToSign, signature, signatureSpec)
    }

    @Suspendable
    private fun RestSmokeTestInput.getValue(key: String): String {
        return checkNotNull(this.data?.get(key)) { "Failed to find key '${key}' in the REST input args" }
    }

    // ---------- INSERTED FOR PERFORMANCE TESTING ----------

    private val mockPublicKey: PublicKey by lazy {
        generatePublicKey()
    }

    private val mockSignatureSpec by lazy {
        MockSignatureSpec()
    }

    private fun generatePublicKey(): PublicKey {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val key = keyGen.generateKeyPair().public
        val serKey = serializationService.serialize(key)
        return serializationService.deserialize(serKey, PublicKey::class.java)
    }

    private class MockSignatureSpec : SignatureSpec {
        override fun getSignatureName(): String =
            "FlowEnginePerformanceTest-MockSignature"
    }
}
