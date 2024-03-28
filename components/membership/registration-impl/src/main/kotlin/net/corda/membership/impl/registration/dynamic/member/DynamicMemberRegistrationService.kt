package net.corda.membership.impl.registration.dynamic.member

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.ShortHashException
import net.corda.crypto.core.fullId
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.toByteArray
import net.corda.crypto.hes.EphemeralKeyPairEncryptor
import net.corda.crypto.hes.HybridEncryptionParams
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequestHeader
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessageHeader
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
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.verifiers.OrderVerifier
import net.corda.membership.impl.registration.verifiers.P2pEndpointVerifier
import net.corda.membership.impl.registration.verifiers.RegistrationContextCustomFieldsVerifier
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.REGISTRATION_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.TLS_CERTIFICATE_SUBJECT
import net.corda.membership.lib.MemberInfoExtension.Companion.ecdhKey
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toWire
import net.corda.membership.lib.verifyReRegistrationChanges
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.p2p.helpers.KeySpecExtractor
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.spec
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.validateSchemeAndSignatureSpec
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.membership.MembershipSchema.RegistrationContextSchema
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.versioning.Version
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.UUID

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
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = LocallyHostedIdentitiesService::class)
    private val locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
    @Reference(service = ConfigurationGetService::class)
    private val configurationGetService: ConfigurationGetService,
) : MemberRegistrationService {
    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerRegistrationService : AutoCloseable {
        fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): Collection<Record<*, *>>
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val clock: Clock = UTCClock()

        const val SESSION_KEY_ID = "$PARTY_SESSION_KEYS.id"
        const val LEDGER_KEY_ID = "$LEDGER_KEYS.%s.id"
        const val NOTARY_KEY_ID = "corda.notary.keys.%s.id"
        const val LEDGER_KEY_SIGNATURE_SPEC = "$LEDGER_KEYS.%s.signature.spec"
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"

        val notaryIdRegex = NOTARY_KEY_ID.format("[0-9]+").toRegex()
        val ledgerIdRegex = LEDGER_KEY_ID.format("[0-9]+").toRegex()
        val sessionKeyIdRegex = SESSION_KEY_ID.format("([0-9]+)").toRegex()
        val notaryProtocolVersionsRegex = NOTARY_SERVICE_PROTOCOL_VERSIONS.format("[0-9]+").toRegex()

        val REGISTRATION_CONTEXT_FIELDS = setOf(PRE_AUTH_TOKEN)
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
    private val registrationContextCustomFieldsVerifier = RegistrationContextCustomFieldsVerifier()

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
    ) = impl.register(registrationId, member, context)

    private object InactiveImpl : InnerRegistrationService {
        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): Collection<Record<*, *>> {
            logger.warn("DynamicMemberRegistrationService is currently inactive.")
            throw NotReadyMembershipRegistrationException(
                "Registration failed. Reason: DynamicMemberRegistrationService is not running."
            )
        }

        override fun close() = Unit
    }

    private inner class ActiveImpl : InnerRegistrationService {
        @Suppress("LongMethod")
        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>,
        ): Collection<Record<*, *>> {
            val registrationLogger = RegistrationLogger(logger)
                .setRegistrationId(registrationId.toString())
                .setMember(member)
            try {
                membershipSchemaValidatorFactory
                    .createValidator()
                    .validateRegistrationContext(
                        RegistrationContextSchema.DynamicMember,
                        Version(1, 0),
                        context
                    )
            } catch (ex: MembershipSchemaValidationException) {
                throw InvalidMembershipRegistrationException(
                    "Registration failed. The registration context is invalid. " + ex.message,
                    ex
                )
            }
            try {
                validateContext(context)
            } catch (ex: IllegalArgumentException) {
                throw InvalidMembershipRegistrationException(
                    "Registration failed. The registration context is invalid: " + ex.message,
                    ex,
                )
            }
            val customFieldsValid = registrationContextCustomFieldsVerifier.verify(context)
            if (customFieldsValid is RegistrationContextCustomFieldsVerifier.Result.Failure) {
                val errorMessage = "Registration failed. ${customFieldsValid.reason}"
                registrationLogger.warn(errorMessage)
                throw InvalidMembershipRegistrationException(errorMessage)
            }
            return try {
                val memberId = member.shortHash
                val roles = MemberRole.extractRolesFromContext(context)
                val notaryKeys = generateNotaryKeys(context, memberId.value)
                logger.debug("Member roles: {}, notary keys: {}", roles, notaryKeys)
                val groupReader = membershipGroupReaderProvider.getGroupReader(member)
                val previousInfo = groupReader.lookup(member.x500Name, MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
                val platformTransformedMemberContext = buildMemberContext(
                    context,
                    registrationId,
                    member,
                    roles,
                    notaryKeys,
                    previousInfo?.memberProvidedContext?.toMap(),
                ).toSortedMap()
                    .toWire()
                val registrationContext = buildRegistrationContext(context)

                val publicKey = keyEncodingService.decodePublicKey(platformTransformedMemberContext.getFirst(PARTY_SESSION_KEYS_PEM))
                val signatureSpec = platformTransformedMemberContext.getFirst(SESSION_KEYS_SIGNATURE_SPEC)

                // This is the user provided part of registration context with transformations. It will be the member
                // provided context of the member information after successful registration.
                val signedPlatformTransformedMemberContext = sign(memberId, publicKey, signatureSpec, platformTransformedMemberContext)
                // This is the context used during registration process, e.g. pre-auth tokens will be placed here.
                val signedRegistrationContext = sign(memberId, publicKey, signatureSpec, registrationContext)

                // The group reader might not know about the MGM yet.
                val mgm = groupReader.lookup().firstOrNull { it.isMgm }
                    ?: throw NotReadyMembershipRegistrationException("Failed to look up MGM information.")

                val serialInfo = context[SERIAL]?.toLong()
                    ?: previousInfo?.serial
                    ?: 0

                verifyReRegistrationIsEnabled(serialInfo, previousInfo?.serial, mgm.platformVersion)

                val message = MembershipRegistrationRequest(
                    registrationId.toString(),
                    signedPlatformTransformedMemberContext,
                    signedRegistrationContext,
                    serialInfo,
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
                        mgm.holdingIdentity.toAvro(),
                        ByteBuffer.wrap(salt), ByteBuffer.wrap(aad), keyEncodingService.encodeAsString(ek)
                    )
                    HybridEncryptionParams(salt, aad)
                }

                val messageHeader = OutboundUnauthenticatedMessageHeader(
                    mgm.holdingIdentity.toAvro(),
                    member.toAvro(),
                    MEMBERSHIP_P2P_SUBSYSTEM,
                    "Register-${memberId.value}-${UUID.randomUUID()}",
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
                    memberId.value
                )

                val commands = membershipPersistenceClient.setRegistrationRequestStatus(
                    viewOwningIdentity = member,
                    registrationId = registrationId.toString(),
                    registrationRequestStatus = RegistrationStatus.SENT_TO_MGM,
                    serialNumber = serialInfo,
                ).createAsyncCommands()

                listOf(record) + commands
            } catch (e: InvalidMembershipRegistrationException) {
                registrationLogger.warn("Registration failed.", e)
                throw e
            } catch (e: IllegalArgumentException) {
                registrationLogger.warn("Registration failed.", e)
                throw InvalidMembershipRegistrationException(
                    "Registration failed. Reason: ${e.message}",
                    e,
                )
            } catch (e: NotReadyMembershipRegistrationException) {
                throw e
            } catch (e: MembershipPersistenceResult.PersistenceRequestException) {
                registrationLogger.warn("Registration failed.", e)
                throw NotReadyMembershipRegistrationException("Could not persist request: ${e.message}", e)
            } catch (e: Exception) {
                registrationLogger.warn("Registration failed.", e)
                throw NotReadyMembershipRegistrationException(
                    "Registration failed. Reason: ${e.message}",
                    e,
                )
            }
        }

        override fun close() {
            publisher.close()
        }

        private fun buildRegistrationContext(
            context: Map<String, String>
        ) = context.filter {
            REGISTRATION_CONTEXT_FIELDS.contains(it.key)
        }.toSortedMap().toWire()

        private fun buildMemberContext(
            context: Map<String, String>,
            registrationId: UUID,
            member: HoldingIdentity,
            roles: Collection<MemberRole>,
            notaryKeys: List<KeyDetails>,
            previousRegistrationContext: Map<String, String>?,
        ): Map<String, String> {
            val cpi = virtualNodeInfoReadService.get(member)?.cpiIdentifier
                ?: throw CordaRuntimeException("Could not find virtual node info for member ${member.shortHash}")
            val filteredContext = context.filterNot {
                it.key.startsWith(LEDGER_KEYS) ||
                    it.key.startsWith(SESSION_KEYS) ||
                    it.key.startsWith(SERIAL) ||
                    notaryIdRegex.matches(it.key) ||
                    REGISTRATION_CONTEXT_FIELDS.contains(it.key)
            }
            val tlsSubject = getTlsSubject(member)
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
            )
            val roleContext = roles.toMemberInfo { notaryKeys }
            val optionalContext = mapOf(MEMBER_CPI_SIGNER_HASH to cpi.signerSummaryHash.toString())
            val newRegistrationContext = filteredContext +
                sessionKeyContext +
                ledgerKeyContext +
                additionalContext +
                roleContext +
                optionalContext +
                tlsSubject

            previousRegistrationContext?.let {
                val diffInvalidMsg = verifyReRegistrationChanges(previousRegistrationContext, newRegistrationContext)
                if (!diffInvalidMsg.isNullOrEmpty()) {
                    throw InvalidMembershipRegistrationException(
                        diffInvalidMsg
                    )
                }
            }

            return newRegistrationContext
        }

        /**
         * Verify MGM is not on 5.0 platform, since re-registration is not supported by that version.
         * If submitted serial or member's current serial suggests re-registration attempt,
         * we will mark their request as INVALID.
         */
        @Suppress("ComplexCondition")
        private fun verifyReRegistrationIsEnabled(
            submittedSerial: Long,
            currentSerial: Long?,
            mgmPlatformVersion: Int,
        ) {
            if ((submittedSerial > 0 || (currentSerial != null && currentSerial > 0)) && mgmPlatformVersion < 50100) {
                throw InvalidMembershipRegistrationException(
                    "MGM is on a lower version where re-registration " +
                        "is not supported."
                )
            }
        }

        private fun getTlsSubject(member: HoldingIdentity): Map<String, String> {
            return if (TlsType.getClusterType(configurationGetService::getSmartConfig) == TlsType.MUTUAL) {
                val info =
                    locallyHostedIdentitiesService.pollForIdentityInfo(member)
                        ?: throw CordaRuntimeException(
                            "Member $member is not locally hosted. " +
                                "If it had been configured, please retry the registration in a few seconds. " +
                                "If it had not been configured, please configure it using the network/setup API."
                        )
                val certificate = info.tlsCertificates
                    .firstOrNull()
                    ?: throw CordaRuntimeException("Member $member is missing TLS certificates")
                val subject = MemberX500Name.parse(certificate.subjectX500Principal.toString())
                mapOf(TLS_CERTIFICATE_SUBJECT to subject.toString())
            } else {
                emptyMap()
            }
        }

        private fun validateContext(context: Map<String, String>) {
            context.keys.filter { sessionKeyIdRegex.matches(it) }.apply {
                require(isNotEmpty()) { "No session key ID was provided." }
                require(orderVerifier.isOrdered(this, 3)) { "Provided session key IDs are incorrectly numbered." }
                this.forEach {
                    validateKey(it, context[it]!!)
                }
            }
            p2pEndpointVerifier.verifyContext(context)
            val isNotary = context.entries.any { it.key.startsWith(ROLES_PREFIX) && it.value == NOTARY_ROLE }
            context.keys.filter { ledgerIdRegex.matches(it) }.apply {
                if (!isNotary) {
                    require(isNotEmpty()) { "No ledger key ID was provided." }
                } else {
                    require(isEmpty()) { "A ledger key ID was provided for a notary virtual node." }
                }
                require(orderVerifier.isOrdered(this, 3)) { "Provided ledger key IDs are incorrectly numbered." }
                this.forEach {
                    validateKey(it, context[it]!!)
                }
            }
            if (isNotary) {
                context.keys.filter { notaryIdRegex.matches(it) }.apply {
                    require(isNotEmpty()) { "No notary key ID was provided." }
                    require(orderVerifier.isOrdered(this, 3)) { "Provided notary key IDs are incorrectly numbered." }
                    this.forEach {
                        validateKey(it, context[it]!!)
                    }
                }
                context.keys.filter { notaryProtocolVersionsRegex.matches(it) }.apply {
                    require(
                        orderVerifier.isOrdered(
                            this,
                            6
                        )
                    ) { "Provided notary protocol versions are incorrectly numbered." }
                }
            }
        }

        private fun validateKey(contextKey: String, keyId: String) {
            try {
                ShortHash.parse(keyId)
            } catch (e: ShortHashException) {
                throw IllegalArgumentException("Invalid value for key ID $contextKey. ${e.message}", e)
            }
        }

        @Suppress("NestedBlockDepth", "ThrowsCount")
        private fun getKeysFromIds(
            keyIds: Collection<String>,
            tenantId: String,
            expectedCategory: String,
        ): List<CryptoSigningKey> {
            val parsedKeyIds =
                try {
                    keyIds.map { ShortHash.parse(it) }
                } catch (e: ShortHashException) {
                    throw IllegalArgumentException(e)
                }
            return cryptoOpsClient.lookupKeysByIds(tenantId, parsedKeyIds).also { keys ->
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
        }

        private fun getSignatureSpec(
            key: CryptoSigningKey,
            specFromContext: String?,
            specType: KeySpecExtractor.KeySpecType = KeySpecExtractor.KeySpecType.OTHER
        ): SignatureSpec {
            if (specFromContext != null) {
                key.validateSchemeAndSignatureSpec(specFromContext, specType)
                return SignatureSpecImpl(specFromContext)
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
            defaultSpec: String?,
            specType: KeySpecExtractor.KeySpecType = KeySpecExtractor.KeySpecType.OTHER,
        ) : KeyDetails {
            private val publicKey by lazy {
                keyEncodingService.decodePublicKey(key.publicKey.array())
            }
            override val pem by lazy {
                keyEncodingService.encodeAsString(publicKey)
            }
            override val hash by lazy {
                publicKey.fullIdHash()
            }
            override val spec by lazy {
                getSignatureSpec(key, defaultSpec, specType)
            }
        }

        private fun generateLedgerKeyData(context: Map<String, String>, tenantId: String): Map<String, String> {
            val ledgerKeys =
                getKeysFromIds(
                    context.filter {
                        ledgerIdRegex.matches(it.key)
                    }.values,
                    tenantId,
                    LEDGER,
                )
            return ledgerKeys.map {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            }.flatMapIndexed { index, ledgerKey ->
                listOf(
                    String.format(LEDGER_KEYS_KEY, index) to keyEncodingService.encodeAsString(ledgerKey),
                    String.format(LEDGER_KEY_HASHES_KEY, index) to ledgerKey.fullId(),
                    String.format(LEDGER_KEY_SIGNATURE_SPEC, index) to getSignatureSpec(
                        ledgerKeys[index],
                        context[String.format(LEDGER_KEY_SIGNATURE_SPEC, index)],
                    ).signatureName
                )
            }.toMap()
        }

        private fun generateSessionKeyData(context: Map<String, String>, tenantId: String): Map<String, String> {
            return context.entries.map {
                sessionKeyIdRegex.find(it.key)?.groupValues?.get(1) to it.value
            }.mapNotNull {
                if (it.first != null) {
                    it.first to getKeysFromIds(listOf(it.second), tenantId, SESSION_INIT).first()
                } else {
                    null
                }
            }.flatMap { (index, sessionKey) ->
                val sessionPublicKey = keyEncodingService.decodePublicKey(sessionKey.publicKey.array())
                val specKey = String.format(SESSION_KEYS_SIGNATURE_SPEC, index)
                val spec = context[specKey]
                listOf(
                    String.format(PARTY_SESSION_KEYS_PEM, index) to keyEncodingService.encodeAsString(sessionPublicKey),
                    String.format(SESSION_KEYS_HASH, index) to sessionPublicKey.fullId(),
                    specKey to getSignatureSpec(
                        sessionKey,
                        spec,
                        KeySpecExtractor.KeySpecType.SESSION,
                    ).signatureName,
                )
            }.toMap()
        }

        private fun generateNotaryKeys(context: Map<String, String>, tenantId: String): List<KeyDetails> {
            val keyIds = context.filterKeys {
                notaryIdRegex.matches(it)
            }.values
            return getKeysFromIds(keyIds, tenantId, NOTARY).mapIndexed { index, key ->
                Key(
                    key,
                    context[String.format(NOTARY_KEY_SPEC, index)],
                )
            }
        }

        private fun buildUnauthenticatedP2PRequest(
            messageHeader: OutboundUnauthenticatedMessageHeader,
            payload: ByteBuffer,
            topicKey: String,
        ): Record<String, AppMessage> {
            return Record(
                Schemas.P2P.P2P_OUT_TOPIC,
                topicKey,
                AppMessage(
                    OutboundUnauthenticatedMessage(
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
                LifecycleCoordinatorName.forComponent<LocallyHostedIdentitiesService>(),
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

    private fun serialize(context: KeyValuePairList) = wrapWithNullErrorHandling({
        IllegalArgumentException("Failed to serialize the KeyValuePairList for this request.", it)
    }) {
        keyValuePairListSerializer.serialize(context)
    }

    private fun KeyValuePairList.getFirst(key: String): String = key.format(0).let { firstKey ->
        items.first { it.key == firstKey }.value
    }

    private fun sign(
        shortHash: ShortHash,
        publicKey: PublicKey,
        signatureSpec: String,
        data: KeyValuePairList
    ): SignedData {
        val serialised = serialize(data)
        return cryptoOpsClient.sign(
            shortHash.value,
            publicKey,
            SignatureSpecImpl(signatureSpec),
            serialised
        ).let {
            SignedData(
                ByteBuffer.wrap(serialised),
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(it.by)),
                    ByteBuffer.wrap(it.bytes)
                ),
                CryptoSignatureSpec(signatureSpec, null, null),
            )
        }
    }
}
