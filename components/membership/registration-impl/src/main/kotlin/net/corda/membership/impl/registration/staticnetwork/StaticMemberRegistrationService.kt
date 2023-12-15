package net.corda.membership.impl.registration.staticnetwork

import java.nio.ByteBuffer
import java.util.UUID
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.KeyDetails
import net.corda.membership.impl.registration.KeysFactory
import net.corda.membership.impl.registration.MemberRole
import net.corda.membership.impl.registration.MemberRole.Companion.toMemberInfo
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticNetworkGroupParametersUtils.addNotary
import net.corda.membership.impl.registration.staticnetwork.StaticNetworkGroupParametersUtils.signGroupParameters
import net.corda.membership.impl.registration.verifiers.RegistrationContextCustomFieldsVerifier
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.InvalidGroupParametersUpdateException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.membership.MembershipSchema.RegistrationContextSchema
import net.corda.utilities.concurrent.SecManagerForkJoinPool
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.versioning.Version
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService(
    private val groupPolicyProvider: GroupPolicyProvider,
    internal val publisherFactory: PublisherFactory,
    internal val keyEncodingService: KeyEncodingService,
    private val cryptoOpsClient: CryptoOpsClient,
    val configurationReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val hsmRegistrationClient: HSMRegistrationClient,
    private val memberInfoFactory: MemberInfoFactory,
    private val persistenceClient: MembershipPersistenceClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
    private val endpointInfoFactory: EndpointInfoFactory,
    internal val platformInfoProvider: PlatformInfoProvider,
    private val groupParametersFactory: GroupParametersFactory,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipQueryClient: MembershipQueryClient,
    private val clock: Clock
) : MemberRegistrationService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")
        private const val KEY_SCHEME = "corda.key.scheme"
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/static_network_dummy_certificate.pem")!!.readText()

        private const val MAX_PERSISTENCE_RETRIES = 10
    }

    @Activate
    constructor(
        @Reference(service = GroupPolicyProvider::class)
        groupPolicyProvider: GroupPolicyProvider,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = KeyEncodingService::class)
        keyEncodingService: KeyEncodingService,
        @Reference(service = CryptoOpsClient::class)
        cryptoOpsClient: CryptoOpsClient,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = HSMRegistrationClient::class)
        hsmRegistrationClient: HSMRegistrationClient,
        @Reference(service = MemberInfoFactory::class)
        memberInfoFactory: MemberInfoFactory,
        @Reference(service = MembershipPersistenceClient::class)
        persistenceClient: MembershipPersistenceClient,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = MembershipSchemaValidatorFactory::class)
        membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
        @Reference(service = EndpointInfoFactory::class)
        endpointInfoFactory: EndpointInfoFactory,
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider,
        @Reference(service = GroupParametersFactory::class)
        groupParametersFactory: GroupParametersFactory,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = MembershipGroupReaderProvider::class)
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
        @Reference(service = MembershipQueryClient::class)
        membershipQueryClient: MembershipQueryClient,
    ) : this(
        groupPolicyProvider,
        publisherFactory,
        keyEncodingService,
        cryptoOpsClient,
        configurationReadService,
        coordinatorFactory,
        hsmRegistrationClient,
        memberInfoFactory,
        persistenceClient,
        cordaAvroSerializationFactory,
        membershipSchemaValidatorFactory,
        endpointInfoFactory,
        platformInfoProvider,
        groupParametersFactory,
        virtualNodeInfoReadService,
        membershipGroupReaderProvider,
        membershipQueryClient,
        UTCClock()
    )

    // Handler for lifecycle events
    private val lifecycleHandler = RegistrationServiceLifecycleHandler(this)

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        lifecycleHandler
    )
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize key value pair list.") }

    private fun serialize(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw CordaRuntimeException(
            "Failed to serialize key value pair list."
        )
    }

    private val customFieldsVerifier = RegistrationContextCustomFieldsVerifier()

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>
    ): Collection<Record<*, *>> {
        val registrationLogger = RegistrationLogger(logger)
            .setRegistrationId(registrationId.toString())
            .setMember(member)
        if (!isRunning || coordinator.status == LifecycleStatus.DOWN) {
            throw MembershipRegistrationException(
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            )
        }
        try {
            membershipSchemaValidatorFactory
                .createValidator()
                .validateRegistrationContext(
                    RegistrationContextSchema.StaticMember,
                    Version(1, 0),
                    context
                )
        } catch (ex: MembershipSchemaValidationException) {
            val err = "Registration failed. The registration context is invalid: " + ex.message
            registrationLogger.info(err)
            throw InvalidMembershipRegistrationException(err, ex)
        }
        val customFieldsValid = customFieldsVerifier.verify(context)
        if (customFieldsValid is RegistrationContextCustomFieldsVerifier.Result.Failure) {
            val errorMessage = "Registration failed. ${customFieldsValid.reason}"
            registrationLogger.warn(errorMessage)
            throw InvalidMembershipRegistrationException(errorMessage)
        }
        val membershipGroupReader = membershipGroupReaderProvider.getGroupReader(member)
        if (membershipGroupReader.lookup(member.x500Name)?.isActive == true) {
            throw InvalidMembershipRegistrationException(
                "The member ${member.x500Name} had been registered successfully in the group ${member.groupId}. " +
                    "Can not re-register."
            )
        }
        val latestStatuses = membershipQueryClient.queryRegistrationRequests(
            member,
            member.x500Name,
            listOf(RegistrationStatus.APPROVED)
        ).getOrThrow()
        if (latestStatuses.isNotEmpty()) {
            throw InvalidMembershipRegistrationException(
                "The member ${member.x500Name} had been registered successfully in the group ${member.groupId}. " +
                        "See registrations: ${latestStatuses.map { it.registrationId }}. " +
                        "Can not re-register."
            )
        }
        try {
            val roles = MemberRole.extractRolesFromContext(context)
            val customFields = context.filter { it.key.startsWith(CUSTOM_KEY_PREFIX) }
            logger.debug("Roles are: {}", roles)
            val keyScheme = context[KEY_SCHEME] ?: throw IllegalArgumentException("Key scheme must be specified.")
            val groupPolicy = groupPolicyProvider.getGroupPolicy(member)
                ?: throw CordaRuntimeException("Could not find group policy for member: [$member]")
            val staticMemberList = with(groupPolicy.protocolParameters.staticNetworkMembers) {
                requireNotNull(this) { "Could not find static member list in group policy file." }
                map { StaticMember(it, endpointInfoFactory::create) }
            }
            val (memberInfo, records) = parseMemberTemplate(
                member,
                groupPolicy,
                keyScheme,
                roles,
                staticMemberList,
                customFields,
                membershipGroupReader,
            )
            (records + createHostedIdentity(memberInfo, groupPolicy)).publish()

            persistGroupParameters(memberInfo, staticMemberList, membershipGroupReader)

            persistRegistrationRequest(registrationId, memberInfo)

            return emptyList()
        } catch (e: InvalidMembershipRegistrationException) {
            registrationLogger.warn("Registration failed. Reason:", e)
            throw e
        } catch (e: IllegalArgumentException) {
            registrationLogger.warn("Registration failed. Reason:", e)
            throw InvalidMembershipRegistrationException("Registration failed. Reason: ${e.message}", e)
        } catch (e: MembershipPersistenceResult.PersistenceRequestException) {
            registrationLogger.warn("Registration failed. Reason:", e)
            throw NotReadyMembershipRegistrationException("Registration failed. Reason: ${e.message}", e)
        } catch(e: InvalidGroupParametersUpdateException) {
            registrationLogger.warn("Registration failed. Reason:", e)
            throw InvalidMembershipRegistrationException("Registration failed. Reason: ${e.message}", e)
        } catch (e: Exception) {
            registrationLogger.warn("Registration failed. Reason:", e)
            throw NotReadyMembershipRegistrationException("Registration failed. Reason: ${e.message}", e)
        }
    }

    private fun List<Record<*, *>>.publish() {
        lifecycleHandler.publisher.publish(this).forEach {
            it.get()
        }
    }

    private fun persistGroupParameters(
        memberInfo: MemberInfo,
        staticMemberList: List<StaticMember>,
        membershipGroupReader: MembershipGroupReader,
    ) {
        val staticNetworkInfo = getCurrentStaticNetworkConfigWithRetry(membershipGroupReader, memberInfo)

        val avroSignedGroupParameters = staticNetworkInfo.signGroupParameters(
            keyValuePairListSerializer,
            keyEncodingService,
            groupParametersFactory
        )
        val signedGroupParameters = groupParametersFactory.create(avroSignedGroupParameters)

        val holdingIdentity = memberInfo.holdingIdentity
        // Persist group parameters for this member, and publish to Kafka.
        persistenceClient.persistGroupParameters(holdingIdentity, signedGroupParameters).getOrThrow()

        // If this member is a notary, persist updated group parameters for other members who have a vnode set up.
        // Also publish to Kafka.
        if (memberInfo.isNotary()) {
            SecManagerForkJoinPool.pool.submit {
                staticMemberList
                    .parallelStream()
                    .map { MemberX500Name.parse(it.name!!) }
                    .filter { it != memberInfo.name }
                    .map { HoldingIdentity(it, memberInfo.groupId) }
                    .filter { virtualNodeInfoReadService.get(it) != null }
                    .forEach {
                        persistenceClient.persistGroupParameters(it, signedGroupParameters).getOrThrow()
                    }
            }.join()
        }
    }

    private fun persistRegistrationRequest(registrationId: UUID, memberInfo: MemberInfo) {
        val memberContext = serialize(memberInfo.memberProvidedContext.toAvro())
        val registrationContext = serialize(KeyValuePairList(emptyList()))
        persistenceClient.persistRegistrationRequest(
            viewOwningIdentity = memberInfo.holdingIdentity,
            registrationRequest = RegistrationRequest(
                status = RegistrationStatus.APPROVED,
                registrationId = registrationId.toString(),
                requester = memberInfo.holdingIdentity,
                memberContext = SignedData(
                    ByteBuffer.wrap(memberContext),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                registrationContext = SignedData(
                    ByteBuffer.wrap(registrationContext),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                serial = 0L,
            )
        ).getOrThrow()
    }

    private fun validateNotaryDetails(
        registeringMember: StaticMember,
        staticMemberList: List<StaticMember>,
        notaryInfo: Collection<Pair<String, String>>,
        membershipGroupReader: MembershipGroupReader,
    ) {
        val serviceName = MemberX500Name.parse(
            notaryInfo.first { it.first == MemberInfoExtension.NOTARY_SERVICE_NAME }.second
        )
        val registeringMemberName = registeringMember.name?.let {
            MemberX500Name.parse(it)
        }

        //The notary service x500 name is different from the notary virtual node being registered.
        require(
            registeringMemberName != serviceName
        ) {
            "Notary service name invalid: Notary service name $serviceName and virtual node name cannot be the same."
        }
        //The notary service x500 name is different from any existing virtual node x500 name (notary or otherwise).
        require(
            staticMemberList.none { MemberX500Name.parse(it.name!!) == serviceName }
        ) {
            "Notary service name invalid: There is a virtual node having the same name $serviceName."
        }
        // Allow only a single notary virtual node under each notary service.
        require(
            membershipGroupReader
                .lookup()
                .filter { it.name != registeringMemberName }
                .none { it.notaryDetails?.serviceName == serviceName }
        ) {
            throw InvalidMembershipRegistrationException("Notary service '$serviceName' already exists.")
        }
    }

    /**
     * Parses the static member list template, creates the MemberInfo for the registering member and the records for the
     * kafka publisher.
     */
    @Suppress("ThrowsCount")
    private fun parseMemberTemplate(
        registeringMember: HoldingIdentity,
        groupPolicy: GroupPolicy,
        keyScheme: String,
        roles: Collection<MemberRole>,
        staticMemberList: List<StaticMember>,
        customFields: Map<String, String>,
        membershipGroupReader: MembershipGroupReader,
    ): Pair<MemberInfo, List<Record<String, PersistentMemberInfo>>> {
        validateStaticMemberList(staticMemberList)

        val memberName = registeringMember.x500Name
        val memberId = registeringMember.shortHash.value

        val staticMemberInfo = staticMemberList.singleOrNull {
            MemberX500Name.parse(it.name!!) == memberName
        } ?: throw IllegalArgumentException(
            "Our membership $memberName is either not listed in the static member list or there is another member " +
                    "with the same name."
        )

        validateStaticMemberDeclaration(staticMemberInfo)
        // single key scheme used for both session and ledger key
        val keysFactory = KeysFactory(
            cryptoOpsClient,
            keyEncodingService,
            keyScheme,
            memberId,
        )

        fun configureNotaryKey(): List<KeyDetails> {
            hsmRegistrationClient.assignSoftHSM(memberId, NOTARY)
            return listOf(keysFactory.getOrGenerateKeyPair(NOTARY))
        }

        val notaryInfo = roles.toMemberInfo(::configureNotaryKey)
        // validate if provided notary details are correct to fail-fast,
        // before assigning more HSMs, generating other keys for member
        if (notaryInfo.isNotEmpty()) {
            validateNotaryDetails(staticMemberInfo, staticMemberList, notaryInfo, membershipGroupReader)
        }

        hsmRegistrationClient.assignSoftHSM(memberId, LEDGER)
        val ledgerKey = keysFactory.getOrGenerateKeyPair(LEDGER)
        val ledgerKeyEntries = if (roles.any { it is MemberRole.Notary }) {
            emptyMap()
        } else {
            mapOf(
                LEDGER_KEYS_KEY.format(0) to ledgerKey.pem,
                LEDGER_KEY_HASHES_KEY.format(0) to ledgerKey.hash.toString(),
            )
        }

        val sessionKey = when (groupPolicy.protocolParameters.sessionKeyPolicy) {
            SessionKeyPolicy.DISTINCT -> {
                hsmRegistrationClient.assignSoftHSM(memberId, SESSION_INIT)
                keysFactory.getOrGenerateKeyPair(SESSION_INIT)
            }

            SessionKeyPolicy.COMBINED -> {
                ledgerKey
            }
        }

        val cpi = virtualNodeInfoReadService.get(registeringMember)?.cpiIdentifier
            ?: throw CordaRuntimeException("Could not find virtual node info for member ${registeringMember.shortHash}")

        val optionalContext = mapOf(MEMBER_CPI_SIGNER_HASH to cpi.signerSummaryHash.toString())

        @Suppress("SpreadOperator")
        val memberContext = mapOf(
            PARTY_NAME to memberName.toString(),
            String.format(PARTY_SESSION_KEYS_PEM, 0) to sessionKey.pem,
            String.format(SESSION_KEYS_HASH, 0) to sessionKey.hash.toString(),
            GROUP_ID to groupPolicy.groupId,
            *convertEndpoints(staticMemberInfo).toTypedArray(),
            *notaryInfo.toTypedArray(),
            SOFTWARE_VERSION to platformInfoProvider.localWorkerSoftwareVersion,
            PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
            MEMBER_CPI_NAME to cpi.name,
            MEMBER_CPI_VERSION to cpi.version,
        ) + ledgerKeyEntries + optionalContext + customFields

        val memberInfo = memberInfoFactory.createMemberInfo(
            memberContext.toSortedMap(),
            sortedMapOf(
                STATUS to staticMemberInfo.status,
                MODIFIED_TIME to staticMemberInfo.modifiedTime,
                SERIAL to staticMemberInfo.serial,
            )
        )

        return memberInfo to staticMemberList.map {
            val owningMemberHoldingIdentity = HoldingIdentity(MemberX500Name.parse(it.name!!), groupPolicy.groupId)
            Record(
                MEMBER_LIST_TOPIC,
                "${owningMemberHoldingIdentity.shortHash}-$memberId",
                memberInfoFactory.createMgmOrStaticPersistentMemberInfo(
                    owningMemberHoldingIdentity.toAvro(),
                    memberInfo,
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf()),
                    ),
                    CryptoSignatureSpec("", null, null),
                )
            )
        }
    }

    /**
     * Creates the locally hosted identity required for the P2P layer.
     */
    private fun createHostedIdentity(
        registeringMember: MemberInfo,
        groupPolicy: GroupPolicy
    ): Record<String, HostedIdentityEntry> {
        val holdingId = registeringMember.holdingIdentity
        val memberName = holdingId.x500Name
        val memberId = holdingId.shortHash
        val groupId = groupPolicy.groupId

        /**
         * In the case of a static network, we do not need any TLS certificates or session initiation keys as communication will be purely
         * internal within the cluster. For this reason, we pass through a set of "dummy" certificates/keys.
         */
        val hostedIdentity = HostedIdentityEntry(
            net.corda.data.identity.HoldingIdentity(memberName.toString(), groupId),
            memberId.value,
            listOf(DUMMY_CERTIFICATE),
            HostedIdentitySessionKeyAndCert(
                keyEncodingService.encodeAsString(registeringMember.sessionInitiationKeys.first()),
                null
            ),
            emptyList()
        )

        return Record(
            P2P_HOSTED_IDENTITIES_TOPIC,
            holdingId.shortHash.value,
            hostedIdentity
        )
    }

    private fun validateStaticMemberList(members: List<StaticMember>) {
        require(members.isNotEmpty()) { "Static member list inside the group policy file cannot be empty." }
        members.forEach {
            require(!it.name.isNullOrBlank()) { "Member's name is not provided in static member list." }
        }
    }

    private fun validateStaticMemberDeclaration(member: StaticMember) {
        require(
            member.keys.any { it.startsWith(endpointUrlIdentifier) }
        ) { "Endpoint urls are not provided." }
        require(
            member.keys.any { it.startsWith(endpointProtocolIdentifier) }
        ) { "Endpoint protocols are not provided." }
    }

    /**
     * Mapping the keys from the json format to the keys expected in the [MemberInfo].
     */
    private fun convertEndpoints(member: StaticMember): List<Pair<String, String>> {
        val endpoints = mutableListOf<EndpointInfo>()
        member.keys.filter { it.startsWith(endpointUrlIdentifier) }.size.apply {
            for (index in 1..this) {
                endpoints.add(member.getEndpoint(index))
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    private fun getCurrentStaticNetworkConfigWithRetry(
        membershipGroupReader: MembershipGroupReader,
        memberInfo: MemberInfo,
        attempt: Int = 0
    ): StaticNetworkInfo {
        return try {
            val currentStaticNetworkInfo = membershipQueryClient
                .queryStaticNetworkInfo(memberInfo.groupId)
                .getOrThrow()

            // If the current member is a notary then the group parameters need to be updated
            memberInfo.notaryDetails?.let { notary ->
                val currentProtocolVersions = membershipGroupReader
                    .notaryVirtualNodeLookup
                    .getNotaryVirtualNodes(notary.serviceName)
                    .filter { it.name != memberInfo.name }
                    .map { it.notaryDetails!!.serviceProtocolVersions.toHashSet() }
                    .reduceOrNull { acc, it -> acc.apply { retainAll(it) } }
                    ?: emptySet()
                val latestGroupParameters = currentStaticNetworkInfo.groupParameters
                latestGroupParameters?.addNotary(
                    memberInfo,
                    currentProtocolVersions,
                    keyEncodingService,
                    clock
                )
            }?.let { groupParameters ->
                StaticNetworkInfo(
                    currentStaticNetworkInfo.groupId,
                    groupParameters,
                    currentStaticNetworkInfo.mgmPublicSigningKey,
                    currentStaticNetworkInfo.mgmPrivateSigningKey,
                    currentStaticNetworkInfo.version
                ).also {
                    persistenceClient.updateStaticNetworkInfo(it).getOrThrow()
                }
            } ?: currentStaticNetworkInfo
        } catch (ex: MembershipPersistenceResult.PersistenceRequestException) {
            if (attempt < MAX_PERSISTENCE_RETRIES - 1) {
                getCurrentStaticNetworkConfigWithRetry(membershipGroupReader, memberInfo, attempt + 1)
            } else {
                throw ex
            }
        }
    }
}
