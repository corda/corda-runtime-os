package net.cordapp.testing.smoketests.flow

import java.time.Instant
import java.util.UUID
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
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.cordapp.testing.bundles.dogs.Dog
import net.cordapp.testing.smoketests.flow.context.launchContextPropagationFlows
import net.cordapp.testing.smoketests.flow.messages.InitiatedSmokeTestMessage
import net.cordapp.testing.smoketests.flow.messages.JsonSerializationFlowOutput
import net.cordapp.testing.smoketests.flow.messages.JsonSerializationInput
import net.cordapp.testing.smoketests.flow.messages.JsonSerializationOutput
import net.cordapp.testing.smoketests.flow.messages.RpcSmokeTestInput
import net.cordapp.testing.smoketests.flow.messages.RpcSmokeTestOutput
import org.slf4j.LoggerFactory

@Suppress("unused", "TooManyFunctions")
@InitiatingFlow(protocol = "smoke-test-protocol")
class RpcSmokeTestFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val commandMap: Map<String, (RpcSmokeTestInput) -> String> = mapOf(
        "echo" to this::echo,
        "throw_error" to this::throwError,
        "start_sessions" to this::startSessions,
        "persistence_persist" to this::persistencePersistDog,
        "persistence_persist_bulk" to this::persistencePersistDogs,
        "persistence_delete" to this::persistenceDeleteDog,
        "persistence_delete_bulk" to this::persistenceDeleteDogs,
        "persistence_merge" to this::persistenceMergeDog,
        "persistence_merge_bulk" to this::persistenceMergeDogs,
        "persistence_find" to this::persistenceFindDog,
        "persistence_find_bulk" to this::persistenceFindDogs,
        "persistence_findall" to { persistenceFindAllDogs() },
        "persistence_query" to { persistenceQueryDogs() },
        "throw_platform_error" to this::throwPlatformError,
        "subflow_passed_in_initiated_session" to { createSessionsInInitiatingFlowAndPassToInlineFlow(it, true) },
        "subflow_passed_in_non_initiated_session" to { createSessionsInInitiatingFlowAndPassToInlineFlow(it, false) },
        "flow_messaging_apis" to { createMultipleSessionsSingleFlowAndExerciseFlowMessaging(it) },
        "crypto_sign_and_verify" to this::signAndVerify,
        "crypto_verify_invalid_signature" to this::verifyInvalidSignature,
        "crypto_get_default_signature_spec" to this::getDefaultSignatureSpec,
        "crypto_get_compatible_signature_specs" to this::getCompatibleSignatureSpecs,
        "crypto_find_my_signing_keys" to this::findMySigningKeys,
        "context_propagation" to { contextPropagation() },
        "serialization" to this::serialization,
        "lookup_member_by_x500_name" to this::lookupMember,
        "json_serialization" to this::jsonSerialization
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
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var signatureSpecService: SignatureSpecService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, RpcSmokeTestInput::class.java)
        return jsonMarshallingService.format(execute(request))
    }

    private fun echo(input: RpcSmokeTestInput): String {
        return input.getValue("echo_value")
    }

    private fun throwError(input: RpcSmokeTestInput): String {
        throw IllegalStateException(input.getValue("error_message"))
    }

    @Suspendable
    private fun persistencePersistDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        val dog = Dog(dogId, "dog", Instant.now(), "none")
        persistenceService.persist(dog)
        return "dog '${dogId}' saved"
    }

    @Suspendable
    private fun persistencePersistDogs(input: RpcSmokeTestInput): String {
        val dogs = getDogIds(input).map { id -> Dog(id, "dog-$id", Instant.now(), "none") }
        persistenceService.persist(dogs)
        return "dogs ${dogs.map { it.id }} saved"
    }

    @Suspendable
    private fun persistenceDeleteDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        persistenceService.remove(Dog(dogId, "dog", Instant.now(), "none"))
        return "dog '${dogId}' deleted"
    }

    @Suspendable
    private fun persistenceDeleteDogs(input: RpcSmokeTestInput): String {
        val dogs = getDogIds(input).map { id -> Dog(id, "dog-$id", Instant.now(), "none") }
        persistenceService.remove(dogs)
        return "dogs ${dogs.map { it.id }} deleted"
    }

    @Suspendable
    private fun persistenceMergeDog(input: RpcSmokeTestInput): String {
        val dogId = getDogId(input)
        val newDogName = input.getValue("name")
        persistenceService.merge(Dog(dogId, newDogName, Instant.now(), "none"))
        return "dog '${dogId}' merged"
    }

    @Suspendable
    private fun persistenceMergeDogs(input: RpcSmokeTestInput): String {
        val dogs = getDogIds(input).map { id -> Dog(id, "dog-$id", Instant.now(), "merged") }
        persistenceService.merge(dogs)
        return "dogs ${dogs.map { it.id }} merged"
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

    @Suspendable
    private fun persistenceFindDogs(input: RpcSmokeTestInput): String {
        val dogIds = getDogIds(input)
        val dogs = persistenceService.find(Dog::class.java, dogIds)
        return if (dogs.isEmpty()) {
            "no dogs found"
        } else {
            "found dogs ${dogs.map { dog -> "id='${dog.id}' name='${dog.name}" }}"
        }
    }

    @Suspendable
    private fun persistenceFindAllDogs(): String {
        val dogs = persistenceService.findAll(Dog::class.java).execute()
        return if (dogs.isEmpty()) {
            "no dog found"
        } else {
            "found one or more dogs"
        }
    }

    @Suspendable
    private fun persistenceQueryDogs(): String {
        val dogs = persistenceService.query("Dog.all", Dog::class.java).execute()
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
        session.sendAndReceive(InitiatedSmokeTestMessage::class.java, InitiatedSmokeTestMessage("test 1"))
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

    private fun getDogIds(input: RpcSmokeTestInput): List<UUID> {
        val ids = input.getValue("ids").split(";")
        return ids.map { id ->
            try {
                UUID.fromString(id)
            } catch (e: Exception) {
                log.error("your dog must have a valid UUID, '${id}' is no good!")
                throw e
            }
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
                .sendAndReceive(InitiatedSmokeTestMessage::class.java, InitiatedSmokeTestMessage(messages[idx]))

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
    private fun createMultipleSessionsSingleFlowAndExerciseFlowMessaging(
        input: RpcSmokeTestInput
    ): String {
        val sessions = input.getValue("sessions").split(";")
        log.info("SubFlow Flow Messaging - Starting sessions for '${input.getValue("sessions")}'")
        val outputs = mutableListOf<String>()
        sessions.forEachIndexed { _, x500 ->
            val response = flowEngine.subFlow(
                SendReceiveAllMessagingFlow(MemberX500Name.parse(x500))
            )

            outputs.add("${x500}=${response}")
        }

        return outputs.joinToString("; ")
    }

    @Suspendable
    private fun contextPropagation(): String {
        return launchContextPropagationFlows(flowEngine, jsonMarshallingService)
    }

    @Suspendable
    private fun serialization(input: RpcSmokeTestInput): String {
        val serialized = serializationService.serialize(input.getValue("data"))
        return serializationService.deserialize(serialized, String::class.java)
    }

    @Suspendable
    private fun signAndVerify(input: RpcSmokeTestInput): String {
        val x500Name = input.getValue("memberX500")
        val member = memberLookup.lookup(MemberX500Name.parse(x500Name))
        checkNotNull(member) { "Member $x500Name could not be looked up" }
        val publicKey = member.ledgerKeys[0]
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        log.info("Crypto - Signing bytes $bytesToSign with public key '$publicKey'")
        val signedBytes = signingService.sign(bytesToSign, publicKey, SignatureSpec.ECDSA_SHA256)
        log.info("Crypto - Signature $signedBytes received")
        digitalSignatureVerificationService.verify(
            publicKey,
            SignatureSpec.ECDSA_SHA256,
            signedBytes.bytes,
            bytesToSign
        )
        log.info("Crypto - Verified $signedBytes as the signature of $bytesToSign")
        return true.toString()
    }

    @Suspendable
    private fun verifyInvalidSignature(input: RpcSmokeTestInput): String {
        val x500Name = input.getValue("memberX500")
        val member = memberLookup.lookup(MemberX500Name.parse(x500Name))
        checkNotNull(member) { "Member $x500Name could not be looked up" }
        val publicKey = member.ledgerKeys[0]
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        log.info("Crypto - Signing bytes $bytesToSign with public key '$publicKey'")
        val signedBytes = signingService.sign(bytesToSign, publicKey, SignatureSpec.ECDSA_SHA256)
        log.info("Crypto - Signature $signedBytes received")
        return try {
            digitalSignatureVerificationService.verify(
                publicKey,
                SignatureSpec.RSA_SHA256,
                signedBytes.bytes,
                bytesToSign
            )
            false
        } catch (e: CryptoSignatureException) {
            log.info("Crypto - Failed to verify $signedBytes as the signature of $bytesToSign when using wrong signature spec")
            true
        }.toString()
    }

    @Suspendable
    private fun getDefaultSignatureSpec(input: RpcSmokeTestInput): String {
        val x500Name = input.getValue("memberX500")
        val member = memberLookup.lookup(MemberX500Name.parse(x500Name))
        checkNotNull(member) { "Member $x500Name could not be looked up" }
        val publicKey = member.ledgerKeys[0]
        val digestName = try {
            input.getValue("digestName")
        } catch (e: IllegalStateException) {
            null
        }
        log.info("Crypto - Calling default signature spec with public key: $publicKey and digestName: $digestName ")

        val defaultSignatureSpec = if (digestName != null) {
            signatureSpecService.defaultSignatureSpec(publicKey, DigestAlgorithmName(digestName))
        } else {
            signatureSpecService.defaultSignatureSpec(publicKey)
        }
        return defaultSignatureSpec?.signatureName ?: "null"
    }

    @Suspendable
    private fun getCompatibleSignatureSpecs(input: RpcSmokeTestInput): String {
        val x500Name = input.getValue("memberX500")
        val member = memberLookup.lookup(MemberX500Name.parse(x500Name))
        checkNotNull(member) { "Member $x500Name could not be looked up" }
        val publicKey = member.ledgerKeys[0]
        val digestName = try {
            input.getValue("digestName")
        } catch (e: IllegalStateException) {
            null
        }
        log.info("Crypto - Calling compatible signature specs with public key: $publicKey and digestName: $digestName ")

        val compatibleSignatureSpecs = if (digestName != null) {
            signatureSpecService.compatibleSignatureSpecs(publicKey, DigestAlgorithmName(digestName))
        } else {
            signatureSpecService.compatibleSignatureSpecs(publicKey)
        }
        val outputs = compatibleSignatureSpecs.map {
            it.signatureName
        }
        return outputs.joinToString("; ")
    }

    @Suppress("unused_parameter")
    @Suspendable
    private fun findMySigningKeys(input: RpcSmokeTestInput): String {
        val myInfo = memberLookup.myInfo()
        val myKeysFromMemberInfo = myInfo.ledgerKeys.toSet()
        val myKeysFromCryptoWorker = signingService.findMySigningKeys(myKeysFromMemberInfo)
        val requestResponseKey =
            myKeysFromCryptoWorker
                .map {
                    it
                }.single()

        require(requestResponseKey.value != null) {
            "Requested key was not found"
        }
        require(requestResponseKey.key == requestResponseKey.value) {
            "Response key should be same with the requested"
        }
        require(myKeysFromMemberInfo.single() == requestResponseKey.key) {
            "Request key in mapping should match specified request key"
        }
        return "success"
    }

    @Suspendable
    private fun lookupMember(input: RpcSmokeTestInput): String {
        val memberX500Name = input.getValue("id")
        val memberInfo = memberLookup.lookup(MemberX500Name.parse(memberX500Name))
        checkNotNull(memberInfo) { IllegalStateException("Failed to find MemberInfo for $memberX500Name") }

        return memberInfo.name.toString()
    }

    private fun RpcSmokeTestInput.getValue(key: String): String {
        return checkNotNull(this.data?.get(key)) { "Failed to find key '${key}' in the RPC input args" }
    }

    @Suspendable
    private fun execute(input: RpcSmokeTestInput): RpcSmokeTestOutput {
        return RpcSmokeTestOutput(
            checkNotNull(input.command) { "No smoke test command received" },
            checkNotNull(commandMap[input.command]) { "command '${input.command}' not recognised" }.invoke(input)
        )
    }

    @Suspendable
    private fun jsonSerialization(input: RpcSmokeTestInput): String {
        // First test checks custom serializers with message defined in the CorDapp
        // this should output json with 2 fields each with test-string as the value
        val jsonString = jsonMarshallingService.format(JsonSerializationInput("test-string"))
        // this should combine both of those fields
        val jsonOutput = jsonMarshallingService.parse(jsonString, JsonSerializationOutput::class.java)
        // when the second serializer runs during format of JsonSerializationFlowOutput, we should see the combined value
        // outputted as "serialized-implicitly"

        // Second test checks platform custom serializer/deserializer of MemberX500Name, the serializer should be run
        // implicitly when JsonSerializationFlowOutput is formatted
        val memberX500NameString = input.getValue("vnode")
        val memberX500NameDeserialized = jsonMarshallingService.parse("\"$memberX500NameString\"", MemberX500Name::class.java)

        val output = JsonSerializationFlowOutput(
            firstTest = jsonOutput,
            secondTest = memberX500NameDeserialized
        )

        return jsonMarshallingService.format(output)
    }
}
