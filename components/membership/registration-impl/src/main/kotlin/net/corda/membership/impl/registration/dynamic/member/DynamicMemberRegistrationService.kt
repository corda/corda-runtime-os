package net.corda.membership.impl.registration.dynamic.member

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.toByteArray
import net.corda.crypto.ecies.EciesParams
import net.corda.crypto.ecies.EphemeralKeyPairEncryptor
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequestHeader
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.impl.registration.dynamic.verifiers.OrderVerifier
import net.corda.membership.impl.registration.dynamic.verifiers.P2pEndpointVerifier
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.REGISTRATION_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.ecdhKey
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toWire
import net.corda.membership.p2p.helpers.Verifier.Companion.SIGNATURE_SPEC
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.membership.MembershipSchema.RegistrationContextSchema
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.versioning.Version
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.calculateHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class DynamicMemberRegistrationService @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CordaAvroSerializationFactory::class)
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = MembershipSchemaValidatorFactory::class)
    val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = EphemeralKeyPairEncryptor::class)
    private val ephemeralKeyPairEncryptor: EphemeralKeyPairEncryptor,
) : MemberRegistrationService {
    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerRegistrationService : AutoCloseable {
        fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult
    }

    private companion object {
        val logger: Logger = contextLogger()
        val clock: Clock = UTCClock()

        const val PUBLICATION_TIMEOUT_SECONDS = 30L
        const val SESSION_KEY_ID = "$PARTY_SESSION_KEY.id"
        const val SESSION_KEY_SIGNATURE_SPEC = "$PARTY_SESSION_KEY.signature.spec"
        const val LEDGER_KEY_ID = "$LEDGER_KEYS.%s.id"
        const val LEDGER_KEY_SIGNATURE_SPEC = "$LEDGER_KEYS.%s.signature.spec"
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val SOFTWARE_VERSION_CONST = "5.0.0"
        const val SERIAL_CONST = "1"

        val defaultCodeNameToSpec = mapOf(
            ECDSA_SECP256K1_CODE_NAME to SignatureSpec.ECDSA_SHA256,
            ECDSA_SECP256R1_CODE_NAME to SignatureSpec.ECDSA_SHA256,
            EDDSA_ED25519_TEMPLATE to SignatureSpec.EDDSA_ED25519,
            GOST3410_GOST3411_TEMPLATE to SignatureSpec.GOST3410_GOST3411,
            RSA_CODE_NAME to SignatureSpec.RSA_SHA512,
            SM2_CODE_NAME to SignatureSpec.SM2_SM3,
            SPHINCS256_CODE_NAME to SignatureSpec.SPHINCS256_SHA512,
        )
    }

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: RegistrationHandle? = null

    private var _publisher: Publisher? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    private val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private val registrationRequestSerializer: CordaAvroSerializer<MembershipRegistrationRequest> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize registration request.") }

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize key value pair list.") }

    private val orderVerifier = OrderVerifier()
    private val p2pEndpointVerifier = P2pEndpointVerifier(orderVerifier)

    private val unauthenticatedRegistrationRequestSerializer: CordaAvroSerializer<UnauthenticatedRegistrationRequest> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize registration request.") }

    private var impl: InnerRegistrationService = InactiveImpl

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("DynamicMemberRegistrationService started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("DynamicMemberRegistrationService stopped.")
        coordinator.stop()
    }

    private fun activate(coordinator: LifecycleCoordinator) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl.close()
        impl = InactiveImpl
    }

    override fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>,
    ): MembershipRequestRegistrationResult = impl.register(registrationId, member, context)

    private object InactiveImpl : InnerRegistrationService {
        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult {
            logger.warn("DynamicMemberRegistrationService is currently inactive.")
            return MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: DynamicMemberRegistrationService is not running."
            )
        }

        override fun close() = Unit
    }

    private inner class ActiveImpl : InnerRegistrationService {
        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>,
        ): MembershipRequestRegistrationResult {
            try {
                membershipSchemaValidatorFactory
                    .createValidator()
                    .validateRegistrationContext(
                        RegistrationContextSchema.DynamicMember,
                        Version(1, 0),
                        context
                    )
            } catch (ex: MembershipSchemaValidationException) {
                return MembershipRequestRegistrationResult(
                    MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                    "Registration failed. The registration context is invalid. " + ex.getErrorSummary()
                )
            }
            try {
                validateContext(context)
            } catch (ex: IllegalArgumentException) {
                return MembershipRequestRegistrationResult(
                    MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                    "Registration failed. The registration context is invalid: " + ex.message
                )
            }
            try {
                val memberContext = buildMemberContext(context, registrationId.toString(), member)
                    .toSortedMap()
                    .toWire()
                val serializedMemberContext = keyValuePairListSerializer.serialize(memberContext)
                    ?: throw IllegalArgumentException("Failed to serialize the member context for this request.")
                val publicKey =
                    keyEncodingService.decodePublicKey(memberContext.items.first { it.key == PARTY_SESSION_KEY }.value)
                val signatureSpec = memberContext.items.first { it.key == SESSION_KEY_SIGNATURE_SPEC }.value
                val memberSignature = cryptoOpsClient.sign(
                    member.shortHash.value,
                    publicKey,
                    SignatureSpec(signatureSpec),
                    serializedMemberContext,
                    mapOf(SIGNATURE_SPEC to signatureSpec),
                ).let {
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(it.by)),
                        ByteBuffer.wrap(it.bytes),
                        it.context.toWire()
                    )
                }
                val mgm = membershipGroupReaderProvider.getGroupReader(member).lookup().firstOrNull { it.isMgm }
                    ?: throw IllegalArgumentException("Failed to look up MGM information.")

                val message = MembershipRegistrationRequest(
                    registrationId.toString(),
                    ByteBuffer.wrap(serializedMemberContext),
                    memberSignature
                )

                val mgmKey = mgm.ecdhKey ?: throw IllegalArgumentException("MGM's ECDH key is missing.")
                var latestHeader: UnauthenticatedRegistrationRequestHeader? = null

                val data = ephemeralKeyPairEncryptor.encrypt(
                    mgmKey,
                    registrationRequestSerializer.serialize(message)!!
                ) { ek, sk ->
                    val aad = 1.toByteArray() + clock.instant().toEpochMilli().toByteArray() + keyEncodingService.encodeAsByteArray(ek)
                    val salt = aad + keyEncodingService.encodeAsByteArray(sk)
                    latestHeader = UnauthenticatedRegistrationRequestHeader(
                        ByteBuffer.wrap(salt), ByteBuffer.wrap(aad), keyEncodingService.encodeAsString(ek)
                    )
                    EciesParams( salt, aad )
                }

                val messageHeader = UnauthenticatedMessageHeader(
                    mgm.holdingIdentity.toAvro(),
                    member.toAvro(),
                    MEMBERSHIP_P2P_SUBSYSTEM
                )
                val request = UnauthenticatedRegistrationRequest(
                    latestHeader,
                    ByteBuffer.wrap(data.cipherText)
                )
                val record = buildUnauthenticatedP2PRequest(
                    messageHeader,
                    ByteBuffer.wrap(
                        unauthenticatedRegistrationRequestSerializer.serialize(request)
                    ),
                    // holding identity ID is used as topic key to be able to ensure serial processing of registration
                    // for the same member.
                    member.shortHash.value
                )

                membershipPersistenceClient.persistRegistrationRequest(
                    viewOwningIdentity = member,
                    registrationRequest = RegistrationRequest(
                        status = RegistrationStatus.NEW,
                        registrationId = registrationId.toString(),
                        requester = member,
                        memberContext = ByteBuffer.wrap(serializedMemberContext),
                        signature = memberSignature,
                    )
                )

                publisher.publish(listOf(record)).first().get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Registration failed.", e)
                return MembershipRequestRegistrationResult(
                    MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                    "Registration failed. Reason: ${e.message}"
                )
            }

            return MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED)
        }

        override fun close() {
            publisher.close()
        }

        private fun buildMemberContext(
            context: Map<String, String>,
            registrationId: String,
            member: HoldingIdentity
        ): Map<String, String> {
            return (
                context.filterNot {
                    it.key.startsWith(LEDGER_KEYS) || it.key.startsWith(PARTY_SESSION_KEY)
                } + generateSessionKeyData(context, member.shortHash.value) +
                    generateLedgerKeyData(context, member.shortHash.value) +
                    mapOf(
                        REGISTRATION_ID to registrationId,
                        PARTY_NAME to member.x500Name.toString(),
                        GROUP_ID to member.groupId,
                        PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
                        // temporarily hardcoded
                        SOFTWARE_VERSION to SOFTWARE_VERSION_CONST,
                        SERIAL to SERIAL_CONST,
                    )
                )
        }

        private fun validateContext(context: Map<String, String>) {
            context[SESSION_KEY_ID] ?: throw IllegalArgumentException("No session key ID was provided.")
            p2pEndpointVerifier.verifyContext(context)
            context.keys.filter { LEDGER_KEY_ID.format("[0-9]+").toRegex().matches(it) }.apply {
                require(isNotEmpty()) { "No ledger key ID was provided." }
                require(orderVerifier.isOrdered(this, 3)) { "Provided ledger key IDs are incorrectly numbered." }
            }
        }


        @Suppress("NestedBlockDepth")
        private fun getKeysFromIds(keyIds: List<String>, tenantId: String): List<CryptoSigningKey> =
            with(cryptoOpsClient) {
                lookup(tenantId, keyIds).apply {
                    map { it.id }.apply {
                        keyIds.forEach { keyId ->
                            if (!contains(keyId)) {
                                throw IllegalArgumentException("No key found for tenant: $tenantId under $keyId.")
                            }
                        }
                    }
                }
            }

        private fun getSignatureSpec(key: CryptoSigningKey, specFromContext: String?): SignatureSpec {
            if (specFromContext != null) {
                return SignatureSpec(specFromContext)
            }
            logger.info(
                "Signature spec for key with ID: ${key.id} was not specified. Applying default signature spec " +
                    "for ${key.schemeCodeName}."
            )
            return defaultCodeNameToSpec[key.schemeCodeName]
                ?: throw IllegalArgumentException(
                    "Could not find a suitable signature spec for ${key.schemeCodeName}. " +
                        "Specify signature spec for key with ID: ${key.id} explicitly in the context."
                )
        }

        private fun generateLedgerKeyData(context: Map<String, String>, tenantId: String): Map<String, String> {
            val ledgerKeys =
                getKeysFromIds(
                    context.filter {
                        LEDGER_KEY_ID.format("[0-9]+").toRegex().matches(it.key)
                    }.values.toList(),
                    tenantId
                )
            return ledgerKeys.map {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            }.flatMapIndexed { index, ledgerKey ->
                listOf(
                    String.format(LEDGER_KEYS_KEY, index) to keyEncodingService.encodeAsString(ledgerKey),
                    String.format(LEDGER_KEY_HASHES_KEY, index) to ledgerKey.calculateHash().value,
                    String.format(LEDGER_KEY_SIGNATURE_SPEC, index) to getSignatureSpec(
                        ledgerKeys[index],
                        context[String.format(LEDGER_KEY_SIGNATURE_SPEC, index)]
                    ).signatureName
                )
            }.toMap()
        }

        private fun generateSessionKeyData(context: Map<String, String>, tenantId: String): Map<String, String> {
            val sessionKey = getKeysFromIds(listOf(context[SESSION_KEY_ID]!!), tenantId).first()
            val sessionPublicKey = keyEncodingService.decodePublicKey(sessionKey.publicKey.array())
            return mapOf(
                PARTY_SESSION_KEY to keyEncodingService.encodeAsString(sessionPublicKey),
                SESSION_KEY_HASH to sessionPublicKey.calculateHash().value,
                SESSION_KEY_SIGNATURE_SPEC to getSignatureSpec(sessionKey, context[SESSION_KEY_SIGNATURE_SPEC]).signatureName
            )
        }

        private fun buildUnauthenticatedP2PRequest(
            messageHeader: UnauthenticatedMessageHeader,
            payload: ByteBuffer,
            topicKey: String,
        ): Record<String, AppMessage> {
            return Record(
                Schemas.P2P.P2P_OUT_TOPIC,
                topicKey,
                AppMessage(
                    UnauthenticatedMessage(
                        messageHeader,
                        payload
                    )
                )
            )
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling stop event.")
        deactivate(coordinator)
        componentHandle?.close()
        componentHandle = null
        configHandle?.close()
        configHandle = null
        _publisher?.close()
        _publisher = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        logger.info("Handling registration changed event.")
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }
            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling config changed event.")
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("dynamic-member-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(coordinator)
    }
}
