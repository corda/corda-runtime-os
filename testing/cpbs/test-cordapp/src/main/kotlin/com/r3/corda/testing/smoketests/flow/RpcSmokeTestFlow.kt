package com.r3.corda.testing.smoketests.flow

import com.r3.corda.testing.bundles.dogs.Dog
import com.r3.corda.testing.smoketests.flow.digest.CustomDigestAlgorithm
import com.r3.corda.testing.smoketests.flow.messages.InitiatedSmokeTestMessage
import com.r3.corda.testing.smoketests.flow.messages.JsonSerializationFlowOutput
import com.r3.corda.testing.smoketests.flow.messages.JsonSerializationInput
import com.r3.corda.testing.smoketests.flow.messages.JsonSerializationOutput
import com.r3.corda.testing.smoketests.flow.messages.RpcSmokeTestInput
import com.r3.corda.testing.smoketests.flow.messages.RpcSmokeTestOutput
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowContextPropertyKeys
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
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Suppress("unused", "TooManyFunctions")
@InitiatingFlow(protocol = "smoke-test-protocol")
class RpcSmokeTestFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val commandMap: Map<String, (RpcSmokeTestInput) -> String> = mapOf(
        "echo" to this::echo,
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
        "crypto_sign_and_verify" to this::signAndVerify,
        "crypto_verify_invalid_signature" to this::verifyInvalidSignature,
        "crypto_get_default_signature_spec" to this::getDefaultSignatureSpec,
        "crypto_get_compatible_signature_specs" to this::getCompatibleSignatureSpecs,
        "crypto_find_my_signing_keys" to this::findMySigningKeys,
        "lookup_member_by_x500_name" to this::lookupMember,
        "json_serialization" to this::jsonSerialization,
        "get_cpi_metadata" to { getCpiMetadata() },
        "crypto_CompositeKeyGenerator_works_in_flows" to this::compositeKeyGeneratorWorksInFlows,
        "crypto_get_default_digest_algorithm" to this::getDefaultDigestAlgorithm,
        "crypto_get_supported_digest_algorithms" to this::getSupportedDigestAlgorithms
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

    @CordaInject
    lateinit var compositeKeyGenerator: CompositeKeyGenerator

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var externalMessaging: ExternalMessaging

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, RpcSmokeTestInput::class.java)
        return jsonMarshallingService.format(execute(request))
    }

    private fun echo(input: RpcSmokeTestInput): String {
        return input.getValue("echo_value")
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
            "found dog id='${dog.id}' name='${dog.name}'"
        }
    }

    @Suspendable
    private fun persistenceFindDogs(input: RpcSmokeTestInput): String {
        val dogIds = getDogIds(input)
        val dogs = persistenceService.find(Dog::class.java, dogIds)
        return if (dogs.isEmpty()) {
            "no dogs found"
        } else {
            "found dogs ${dogs.map { dog -> "id='${dog.id}' name='${dog.name}'" }}"
        }
    }

    @Suspendable
    private fun persistenceFindAllDogs(): String {
        val dogs = persistenceService.findAll(Dog::class.java).execute()
        return if (dogs.results.isEmpty()) {
            "no dog found"
        } else {
            "found one or more dogs"
        }
    }

    @Suspendable
    private fun persistenceQueryDogs(): String {
        val dogs = persistenceService.query("Dog.all", Dog::class.java).execute()
        return if (dogs.results.isEmpty()) {
            "no dog found"
        } else {
            "found one or more dogs"
        }
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
    private fun signAndVerify(input: RpcSmokeTestInput): String {
        val x500Name = input.getValue("memberX500")
        val member = memberLookup.lookup(MemberX500Name.parse(x500Name))
        checkNotNull(member) { "Member $x500Name could not be looked up" }
        val publicKey = member.ledgerKeys[0]
        val bytesToSign = byteArrayOf(1, 2, 3, 4, 5)
        log.info("Crypto - Signing bytes $bytesToSign with public key '$publicKey'")
        val signatureSpec =
            signatureSpecService.defaultSignatureSpec(publicKey)
                ?: throw IllegalStateException("Default signature spec not found for key")
        val signedBytes = signingService.sign(bytesToSign, publicKey, signatureSpec)
        log.info("Crypto - Signature $signedBytes received")
        digitalSignatureVerificationService.verify(
            bytesToSign,
            signedBytes.bytes,
            publicKey,
            signatureSpec
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
        val signatureSpec =
            signatureSpecService.defaultSignatureSpec(publicKey)
                ?: throw IllegalStateException("Default signature spec not found for key")
        val signedBytes = signingService.sign(bytesToSign, publicKey, signatureSpec)
        log.info("Crypto - Signature $signedBytes received")
        return try {
            val invalidSignatureSpec =
                signatureSpecService.defaultSignatureSpec(publicKey, DigestAlgorithmName.SHA2_512)
                    ?: throw IllegalStateException("Default signature spec not found for key")
            digitalSignatureVerificationService.verify(
                bytesToSign,
                signedBytes.bytes,
                publicKey,
                invalidSignatureSpec
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

    @Suppress("unused_parameter")
    @Suspendable
    private fun compositeKeyGeneratorWorksInFlows(input: RpcSmokeTestInput): String {
        val someKeys = memberLookup.lookup().flatMap { it.ledgerKeys }
        val keysAndWeights = someKeys.map {
            CompositeKeyNodeAndWeight(it, 1)
        }
        val compositeKey = compositeKeyGenerator.create(keysAndWeights, 1)
        return if (compositeKey is CompositeKey) {
            "SUCCESS"
        } else {
            "FAILURE"
        }
    }

    @Suppress("unused_parameter")
    @Suspendable
    private fun getDefaultDigestAlgorithm(input: RpcSmokeTestInput): String {
        val defaultDigestAlgorithm = digestService.defaultDigestAlgorithm()
        return if (defaultDigestAlgorithm == DigestAlgorithmName.SHA2_256) {
            "SUCCESS"
        } else
            "FAILURE"
    }

    @Suppress("unused_parameter")
    @Suspendable
    private fun getSupportedDigestAlgorithms(input: RpcSmokeTestInput): String {
        val supportedDigestAlgorithms = digestService.supportedDigestAlgorithms()
        val expectedSupportedDigestAlgorithms = linkedSetOf(
            DigestAlgorithmName.SHA2_256,
            DigestAlgorithmName.SHA2_256D,
            DigestAlgorithmName.SHA2_384,
            DigestAlgorithmName.SHA2_512
        ) +
                // check it picks up custom digest algorithms too
                setOf(DigestAlgorithmName(CustomDigestAlgorithm.algorithmName))

        return if (expectedSupportedDigestAlgorithms == supportedDigestAlgorithms) {
            "SUCCESS"
        } else
            "FAILURE"
    }

    @Suspendable
    private fun lookupMember(input: RpcSmokeTestInput): String {
        val memberX500Name = input.getValue("id")
        val memberInfo = memberLookup.lookup(MemberX500Name.parse(memberX500Name))
        checkNotNull(memberInfo) { IllegalStateException("Failed to find MemberInfo for $memberX500Name") }

        return memberInfo.name.toString()
    }

    @Suspendable
    private fun getCpiMetadata(): String {
        return """{
            "cpiName": "${flowEngine.flowContextProperties[FlowContextPropertyKeys.CPI_NAME]}",
            "cpiVersion": "${flowEngine.flowContextProperties[FlowContextPropertyKeys.CPI_VERSION]}",
            "cpiSignerSummaryHash": "${flowEngine.flowContextProperties[FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH]}",
            "cpiFileChecksum": "${flowEngine.flowContextProperties[FlowContextPropertyKeys.CPI_FILE_CHECKSUM]}",
            
            "initialPlatformVersion": "${flowEngine.flowContextProperties[FlowContextPropertyKeys.INITIAL_PLATFORM_VERSION]}",
            "initialSoftwareVersion": "${flowEngine.flowContextProperties[FlowContextPropertyKeys.INITIAL_SOFTWARE_VERSION]}"
        }""".trimIndent()
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
        val memberX500NameDeserialized =
            jsonMarshallingService.parse("\"$memberX500NameString\"", MemberX500Name::class.java)

        val output = JsonSerializationFlowOutput(
            firstTest = jsonOutput,
            secondTest = memberX500NameDeserialized
        )

        return jsonMarshallingService.format(output)
    }
}
