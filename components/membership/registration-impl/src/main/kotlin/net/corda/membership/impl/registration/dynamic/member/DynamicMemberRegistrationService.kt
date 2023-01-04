package net.corda.membership.impl.registration.dynamic.member

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.toByteArray
import net.corda.crypto.hes.EphemeralKeyPairEncryptor
import net.corda.crypto.hes.HybridEncryptionParams
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
import net.corda.membership.impl.registration.KeyDetails
import net.corda.membership.impl.registration.MemberRole
import net.corda.membership.impl.registration.MemberRole.Companion.toMemberInfo
import net.corda.membership.impl.registration.dynamic.verifiers.OrderVerifier
import net.corda.membership.impl.registration.dynamic.verifiers.P2pEndpointVerifier
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.REGISTRATION_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
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
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.spec
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.validateSpecName
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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.versioning.Version
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.calculateHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
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
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
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
        const val NOTARY_KEY_ID = "corda.notary.keys.%s.id"
        const val LEDGER_KEY_SIGNATURE_SPEC = "$LEDGER_KEYS.%s.signature.spec"
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val SERIAL_CONST = "1"

        val notaryIdRegex = NOTARY_KEY_ID.format("[0-9]+").toRegex()
        val ledgerIdRegex = LEDGER_KEY_ID.format("[0-9]+").toRegex()
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
        coordinator.start()
    }

    override fun stop() {
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
                    "Registration failed. The registration context is invalid. " + ex.message
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
                val roles = MemberRole.extractRolesFromContext(context)
                val notaryKeys = generateNotaryKeys(context, member.shortHash.value)
                logger.debug("Member roles: {}, notary keys: {}", roles, notaryKeys)
                val memberContext = buildMemberContext(
                    context,
                    registrationId,
                    member,
                    roles,
                    notaryKeys,
                ).toSortedMap()
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
                    val aad = 1.toByteArray() +
                            clock.instant().toEpochMilli().toByteArray() +
                            keyEncodingService.encodeAsByteArray(ek)
                    val salt = aad + keyEncodingService.encodeAsByteArray(sk)
                    latestHeader = UnauthenticatedRegistrationRequestHeader(
                        ByteBuffer.wrap(salt), ByteBuffer.wrap(aad), keyEncodingService.encodeAsString(ek)
                    )
                    HybridEncryptionParams(salt, aad)
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
            registrationId: UUID,
            member: HoldingIdentity,
            roles: Collection<MemberRole>,
            notaryKeys: List<KeyDetails>,
        ): Map<String, String> {
            val cpi = virtualNodeInfoReadService.get(member)?.cpiIdentifier
                ?: throw CordaRuntimeException("Could not find virtual node info for member ${member.shortHash}")
            val filteredContext = context.filterNot {
                it.key.startsWith(LEDGER_KEYS) || it.key.startsWith(PARTY_SESSION_KEY)
            }
            val sessionKeyContext = generateSessionKeyData(context, member.shortHash.value)
            val ledgerKeyContext = generateLedgerKeyData(context, member.shortHash.value)
            val additionalContext = mapOf(
                REGISTRATION_ID to registrationId.toString(),
                PARTY_NAME to member.x500Name.toString(),
                GROUP_ID to member.groupId,
                PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
                SOFTWARE_VERSION to platformInfoProvider.localWorkerSoftwareVersion,
                MEMBER_CPI_NAME to cpi.name,
                MEMBER_CPI_VERSION to cpi.version,
                SERIAL to SERIAL_CONST,
            )
            val roleContext = roles.toMemberInfo { notaryKeys }
            val optionalContext = cpi.signerSummaryHash?.let {
                mapOf(MEMBER_CPI_SIGNER_HASH to it.toString())
            } ?: emptyMap()
            return filteredContext +
                    sessionKeyContext +
                    ledgerKeyContext +
                    additionalContext +
                    roleContext +
                    optionalContext

        }

        private fun validateContext(context: Map<String, String>) {
            context[SESSION_KEY_ID] ?: throw IllegalArgumentException("No session key ID was provided.")
            p2pEndpointVerifier.verifyContext(context)
            context.keys.filter { ledgerIdRegex.matches(it) }.apply {
                require(isNotEmpty()) { "No ledger key ID was provided." }
                require(orderVerifier.isOrdered(this, 3)) { "Provided ledger key IDs are incorrectly numbered." }
            }
            if (context.entries.any { it.key.startsWith(ROLES_PREFIX) && it.value == NOTARY_ROLE }) {
                context.keys.filter { notaryIdRegex.matches(it) }.apply {
                    require(isNotEmpty()) { "No notary key ID was provided." }
                    require(orderVerifier.isOrdered(this, 3)) { "Provided notary key IDs are incorrectly numbered." }
                }
            }
        }

        @Suppress("NestedBlockDepth")
        private fun getKeysFromIds(
            keyIds: List<String>,
            tenantId: String,
            expectedCategory: String,
        ): List<CryptoSigningKey> =
            cryptoOpsClient.lookup(tenantId, keyIds).also { keys ->
                val ids = keys.onEach { key ->
                    if (key.category != expectedCategory) {
                        throw IllegalArgumentException("Key ${key.id} is not in category $expectedCategory but in ${key.category}")
                    }
                }.map {
                    it.id
                }.toSet()
                val missingKeys = keyIds.filterNot {
                    ids.contains(it)
                }
                if (missingKeys.isNotEmpty()) {
                    throw IllegalArgumentException("No keys found for tenant: $tenantId under $missingKeys.")
                }
            }

        private fun getSignatureSpec(key: CryptoSigningKey, specFromContext: String?): SignatureSpec {
            if (specFromContext != null) {
                key.validateSpecName(specFromContext)
                return SignatureSpec(specFromContext)
            }
            logger.info(
                "Signature spec for key with ID: ${key.id} was not specified. Applying default signature spec " +
                    "for ${key.schemeCodeName}."
            )
            return key.spec ?: throw IllegalArgumentException(
                "Could not find a suitable signature spec for ${key.schemeCodeName}. " +
                    "Specify signature spec for key with ID: ${key.id} explicitly in the context."
            )
        }

        private inner class Key(
            key: CryptoSigningKey,
            defaultSpec: String?
        ) : KeyDetails {
            private val publicKey by lazy {
                keyEncodingService.decodePublicKey(key.publicKey.array())
            }
            override val pem by lazy {
                keyEncodingService.encodeAsString(publicKey)
            }
            override val hash by lazy {
                publicKey.calculateHash()
            }
            override val spec by lazy {
                getSignatureSpec(key, defaultSpec)
            }
        }

        private fun generateLedgerKeyData(context: Map<String, String>, tenantId: String): Map<String, String> {
            val ledgerKeys =
                getKeysFromIds(
                    context.filter {
                        ledgerIdRegex.matches(it.key)
                    }.values.toList(),
                    tenantId,
                    LEDGER,
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
            val sessionKey = getKeysFromIds(listOf(context[SESSION_KEY_ID]!!), tenantId, SESSION_INIT).first()
            val sessionPublicKey = keyEncodingService.decodePublicKey(sessionKey.publicKey.array())
            return mapOf(
                PARTY_SESSION_KEY to keyEncodingService.encodeAsString(sessionPublicKey),
                SESSION_KEY_HASH to sessionPublicKey.calculateHash().value,
                SESSION_KEY_SIGNATURE_SPEC to getSignatureSpec(
                    sessionKey,
                    context[SESSION_KEY_SIGNATURE_SPEC]
                ).signatureName
            )
        }

        private fun generateNotaryKeys(context: Map<String, String>, tenantId: String): List<KeyDetails> {
            val keyIds = context.filterKeys {
                notaryIdRegex.matches(it)
            }.values
                .toList()
            return getKeysFromIds(keyIds, tenantId, NOTARY).mapIndexed { index, key ->
                Key(
                    key,
                    context[String.format(NOTARY_KEY_SPEC, index)]
                )
            }
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
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
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
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("dynamic-member-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(coordinator)
    }
}
