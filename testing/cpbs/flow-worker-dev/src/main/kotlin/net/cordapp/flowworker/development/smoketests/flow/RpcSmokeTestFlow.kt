package net.cordapp.flowworker.development.smoketests.flow

import net.corda.testing.bundles.dogs.Dog
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.failures.CryptoSignatureException
import net.cordapp.flowworker.development.smoketests.flow.messages.InitiatedSmokeTestMessage
import net.cordapp.flowworker.development.smoketests.flow.messages.RpcSmokeTestInput
import net.cordapp.flowworker.development.smoketests.flow.messages.RpcSmokeTestOutput
import net.cordapp.flowworker.development.smoketests.flow.context.launchContextPropagationFlows
import java.time.Instant
import java.util.UUID

@Suppress("unused", "TooManyFunctions")
@InitiatingFlow(protocol = "smoke-test-protocol")
class RpcSmokeTestFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    private val commandMap: Map<String, (RpcSmokeTestInput) -> String> = mapOf(
        "echo" to this::echo,
        "throw_error" to this::throwError,
        "start_sessions" to this::startSessions,
        "persist_insert" to this::persistenceInsertDog,
        "persist_delete" to this::persistenceDeleteDog,
        "persist_update" to this::persistenceUpdateDog,
        "persist_find" to this::persistenceFindDog,
        "persist_findall" to this::persistenceFindAllDogs,
        "throw_platform_error" to this::throwPlatformError,
        "subflow_passed_in_initiated_session" to { createSessionsInInitiatingFlowAndPassToInlineFlow(it, true) },
        "subflow_passed_in_non_initiated_session" to { createSessionsInInitiatingFlowAndPassToInlineFlow(it, false) },
        "crypto_sign_and_verify" to this::signAndVerify,
        "crypto_verify_invalid_signature" to this::verifyInvalidSignature,
        "context_propagation" to this::contextPropagation
    )

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
    lateinit var signingService: SigningService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val request = requestBody.getRequestBodyAs<RpcSmokeTestInput>(jsonMarshallingService)
        return jsonMarshallingService.format(request.execute())
    }

    private fun echo(input: RpcSmokeTestInput): String {
        return input.getValue("echo_value")
    }

    private fun throwError(input: RpcSmokeTestInput): String {
        throw IllegalStateException(input.getValue("error_message"))
    }

    @Suspendable
    private fun persistenceInsertDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        val dog = Dog(dogId, "dog", Instant.now(), "none")
        persistenceService.persist(dog)
        return "dog '${dogId}' saved"
    }

    @Suspendable
    private fun persistenceDeleteDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        persistenceService.remove(Dog(dogId, "dog", Instant.now(), "none"))
        return "dog '${dogId}' deleted"
    }

    @Suspendable
    private fun persistenceUpdateDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        val newDogName = input.getValue("name")
        persistenceService.merge(Dog(dogId, newDogName, Instant.now(), "none"))
        return "dog '${dogId}' updated"
    }

    @Suspendable
    private fun persistenceFindDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        val dog = persistenceService.find(Dog::class.java, dogId)
        return if (dog == null) {
            "no dog found"
        } else {
            "found dog id='${dog.id}' name='${dog.name}"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @Suspendable
    private fun persistenceFindAllDogs(input: RpcSmokeTestInput): String {
        val dogs = persistenceService.findAll(Dog::class.java).execute()
        return if (dogs.isEmpty()) {
            "no dog found"
        } else {
            "found one or more dogs"
        }
    }

    @Suspendable
    private fun throwPlatformError(input: RpcSmokeTestInput): String {
        val x500 = input.getValue("x500")
        log.info("Creating session for '${x500}'...")
        val session = flowMessaging.initiateFlow(MemberX500Name.parse(x500))
        log.info("Sending first time to session for '${x500}'...")
        session.send(InitiatedSmokeTestMessage("test 1"))
        log.info("Closing session for '${session}'...")
        session.close()
        log.info("Try and send on a closed session to generate an error '${session}'...")
        try {
            session.send(InitiatedSmokeTestMessage("test 2"))
        } catch (e: Exception) {
            log.info("Caught exception for '${session}'...", e)
            return e.message ?: "Error with no message"
        }

        return "No error thrown"
    }

    private fun getDogId(input: RpcSmokeTestInput): UUID {
        val id = input.getValue("id")
        return try {
            UUID.fromString(id)
        } catch (e: Exception) {
            log.error("your dog must have a valid UUID, '${id}' is no good!")
            throw e
        }
    }

    @Suspendable
    private fun startSessions(input: RpcSmokeTestInput): String {
        val sessions = input.getValue("sessions").split(";")
        val messages = input.getValue("messages").split(";")
        if (sessions.size != messages.size) {
            throw IllegalStateException("Sessions test run with unmatched messages to sessions")
        }

        log.info("Starting sessions for '${input.getValue("sessions")}'")
        val outputs = mutableListOf<String>()
        sessions.forEachIndexed { idx, x500 ->
            log.info("Creating session for '${x500}'...")
            val session = flowMessaging.initiateFlow(MemberX500Name.parse(x500))

            log.info("Creating session '${session}' now sending and waiting for response ...")
            val response = session
                .sendAndReceive<InitiatedSmokeTestMessage>(InitiatedSmokeTestMessage(messages[idx]))
                .unwrap { it }

            log.info("Received response from session '${session}'.")

            outputs.add("${x500}=${response.message}")
        }

        return outputs.joinToString("; ")
    }

    @Suspendable
    private fun createSessionsInInitiatingFlowAndPassToInlineFlow(
        input: RpcSmokeTestInput,
        initiateSessionInInitiatingFlow: Boolean
    ): String {
        val sessions = input.getValue("sessions").split(";")
        val messages = input.getValue("messages").split(";")
        if (sessions.size != messages.size) {
            throw IllegalStateException("Sessions test run with unmatched messages to sessions")
        }

        log.info("SubFlow - Starting sessions for '${input.getValue("sessions")}'")
        val outputs = mutableListOf<String>()
        sessions.forEachIndexed { idx, x500 ->
            val response = flowEngine.subFlow(
                InitiatingSubFlowSmokeTestFlow(
                    MemberX500Name.parse(x500),
                    initiateSessionInInitiatingFlow,
                    messages[idx]
                )
            )

            outputs.add("${x500}=${response.message}")
        }

        return outputs.joinToString("; ")
    }

    @Suspendable
    private fun contextPropagation(@Suppress("UNUSED_PARAMETER") input: RpcSmokeTestInput): String {
        return launchContextPropagationFlows(flowEngine, jsonMarshallingService)
    }

    @Suspendable
    private fun signAndVerify(input: RpcSmokeTestInput): String {
        val publicKey = signingService.decodePublicKey(input.getValue("publicKey"))
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        log.info("Crypto - Signing bytes $bytesToSign with public key '$publicKey'")
        val signedBytes = signingService.sign(bytesToSign, publicKey, SignatureSpec.RSA_SHA256)
        log.info("Crypto - Signature $signedBytes received")
        digitalSignatureVerificationService.verify(publicKey, SignatureSpec.RSA_SHA256, signedBytes.bytes, bytesToSign)
        log.info("Crypto - Verified $signedBytes as the signature of $bytesToSign")
        return true.toString()
    }

    @Suspendable
    private fun verifyInvalidSignature(input: RpcSmokeTestInput): String {
        val publicKey = signingService.decodePublicKey(input.getValue("publicKey"))
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        log.info("Crypto - Signing bytes $bytesToSign with public key '$publicKey'")
        val signedBytes = signingService.sign(bytesToSign, publicKey, SignatureSpec.RSA_SHA256)
        log.info("Crypto - Signature $signedBytes received")
        return try {
            digitalSignatureVerificationService.verify(
                publicKey,
                SignatureSpec.ECDSA_SHA256,
                signedBytes.bytes,
                bytesToSign
            )
            false
        } catch (e: CryptoSignatureException) {
            log.info("Crypto - Failed to verify $signedBytes as the signature of $bytesToSign when using wrong signature spec")
            true
        }.toString()
    }

    private fun RpcSmokeTestInput.getValue(key: String): String {
        return checkNotNull(this.data?.get(key)) { "Failed to find key '${key}' in the RPC input args" }
    }

    private fun RpcSmokeTestInput.execute(): RpcSmokeTestOutput {
        return RpcSmokeTestOutput(
            checkNotNull(this.command) { "No smoke test command received" },
            checkNotNull(commandMap[this.command]) { "command '${this.command}' not recognised" }.invoke(this)
        )
    }
}
