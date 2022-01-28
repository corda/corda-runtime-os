package net.corda.membership.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.CryptoConsts
import net.corda.crypto.CryptoOpsClient
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.identity.EndpointInfoImpl
import net.corda.membership.identity.MGMContextImpl
import net.corda.membership.identity.MemberContextImpl
import net.corda.membership.identity.MemberInfoExtension
import net.corda.membership.identity.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.identity.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.STATUS
import net.corda.membership.identity.MemberInfoImpl
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.KEY_ALIAS
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.NAME
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MODIFIED_TIME
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_PLATFORM_VERSION
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_SERIAL
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_SOFTWARE_VERSION
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.staticMembers
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.staticMgm
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.membership.conversion.PropertyConverter
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.security.PublicKey
import java.time.Instant

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService @Activate constructor(
    @Reference(service = GroupPolicyProvider::class)
    val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = KeyEncodingService::class)
    val keyEncodingService: KeyEncodingService,
    @Reference(service = CryptoOpsClient::class)
    val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PropertyConverter::class)
    val converter: PropertyConverter,
    @Reference(service = DigestService::class)
    val cipherSuiteFactory: DigestService
) : MemberRegistrationService {
    companion object {
        private val logger: Logger = contextLogger()
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")

        internal const val DEFAULT_SOFTWARE_VERSION = "5.0.0"
        internal const val DEFAULT_PLATFORM_VERSION = "10"
        internal const val DEFAULT_SERIAL = "1"
    }

    private val topic = Schemas.Membership.MEMBER_LIST_TOPIC

    // Handler for lifecycle events
    private val lifecycleHandler = RegistrationServiceLifecycleHandler(this)

    // Component lifecycle coordinator
    private val coordinator =
        coordinatorFactory.createCoordinator<MemberRegistrationService>(lifecycleHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("StaticMemberRegistrationService started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("StaticMemberRegistrationService stopped.")
        coordinator.stop()
    }

    override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult {
        if(!isRunning || coordinator.status == LifecycleStatus.DOWN) {
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            )
        }
        try {
            val updates = lifecycleHandler.publisher.publish(parseMemberTemplate(member))
            updates.forEach { it.get() }
        } catch (e: Exception) {
            logger.warn("Registration failed. Reason: ${e.message}")
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: ${e.message}"
            )
        }
        return MembershipRequestRegistrationResult(SUBMITTED)
    }

    /**
     * Parses the static member list template as SignedMemberInfo objects and creates the records for the
     * kafka publisher.
     */
    private fun parseMemberTemplate(member: HoldingIdentity): List<Record<String, SignedMemberInfo>> {
        val members = mutableListOf<Record<String, SignedMemberInfo>>()

        val policy = groupPolicyProvider.getGroupPolicy(member)
        val groupId = policy.groupId

        val staticMemberList = policy.staticMembers
        require(staticMemberList.isNotEmpty()) { "Static member list inside the group policy file cannot be empty." }

        val processedMembers = mutableListOf<String>()
        @Suppress("SpreadOperator")
        staticMemberList.forEach { staticMember ->
            isValidStaticMemberDeclaration(processedMembers, staticMember)
            val memberName = staticMember[NAME].toString()
            val memberId = HoldingIdentity(memberName, groupId).id
            val memberKey = generateOwningKey(staticMember, memberId)
            val encodedMemberKey = keyEncodingService.encodeAsString(memberKey)
            val mgmKey = parseMgmTemplate(signingService, policy)
            val memberInfo = MemberInfoImpl(
                memberProvidedContext = MemberContextImpl(
                    sortedMapOf(
                        PARTY_NAME to memberName,
                        PARTY_OWNING_KEY to encodedMemberKey,
                        GROUP_ID to groupId,
                        *generateIdentityKeys(encodedMemberKey).toTypedArray(),
                        *convertEndpoints(staticMember).toTypedArray(),
                        SOFTWARE_VERSION to (staticMember[STATIC_SOFTWARE_VERSION] ?: DEFAULT_SOFTWARE_VERSION),
                        PLATFORM_VERSION to (staticMember[STATIC_PLATFORM_VERSION] ?: DEFAULT_PLATFORM_VERSION),
                        SERIAL to (staticMember[STATIC_SERIAL] ?: DEFAULT_SERIAL),
                    ),
                    converter
                ),
                mgmProvidedContext = MGMContextImpl(
                    sortedMapOf(
                        STATUS to (staticMember[MEMBER_STATUS] ?: MEMBER_STATUS_ACTIVE),
                        MODIFIED_TIME to (staticMember[STATIC_MODIFIED_TIME] ?: Instant.now().toString()),
                    ),
                    converter
                )
            )
            members.add(
                Record(
                    topic,
                    member.id + "-" + memberId,
                    signMemberInfo(
                        memberInfo,
                        memberKey,
                        mgmKey,
                        signingService
                    )
                )
            )
            processedMembers.add(memberName)
        }
        return members
    }

    private fun isValidStaticMemberDeclaration(processedMembers: List<String>, member: Map<String, String>) {
        require(!member[NAME].isNullOrBlank()) { "Member's name is not provided." }
        require(!processedMembers.contains(member[NAME]!!)) { "Duplicated static member declaration." }
        require(
            member.keys.any { it.startsWith(endpointUrlIdentifier) }
        ) { "Endpoint urls are not provided." }
        require(
            member.keys.any { it.startsWith(endpointProtocolIdentifier) }
        ) { "Endpoint protocols are not provided." }
    }

    /**
     * If the keyAlias is not defined in the static template, we are going to use the id of the HoldingIdentity as default.
     */
    private fun generateOwningKey(
        member: Map<String, String>,
        memberId: String
    ): PublicKey {
        var keyAlias = member[KEY_ALIAS]
        if (keyAlias.isNullOrBlank()) {
            keyAlias = memberId
        }
        return cryptoOpsClient.generateKeyPair(
            tenantId = memberId,
            category = CryptoConsts.Categories.LEDGER,
            alias = keyAlias
        )
    }

    /**
     * Generates the MGM's key used for signing the MemberInfo from the static template.
     */
    private fun parseMgmTemplate(signingService: SigningService, policy: GroupPolicy): PublicKey {
        val staticMgm = policy.staticMgm
        require(staticMgm.isNotEmpty()) { "Static mgm inside the group policy file should be defined." }

        val keyAlias: String? = staticMgm[KEY_ALIAS]
        require(!keyAlias.isNullOrBlank()) { "MGM's key alias is not provided." }

        return signingService.generateKeyPair(keyAlias)
    }

    /**
     * Mapping the keys from the json format to the keys expected in the [MemberInfo].
     */
    private fun convertEndpoints(member: Map<String, String>): List<Pair<String, String>> {
        val endpoints = mutableListOf<EndpointInfo>()
        member.keys.filter { it.startsWith(endpointUrlIdentifier) }.size.apply {
            for (index in 1..this) {
                endpoints.add(
                    EndpointInfoImpl(
                        member[String.format(ENDPOINT_URL, index)].toString(),
                        member[String.format(ENDPOINT_PROTOCOL, index)]!!.toInt()
                    )
                )
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index].url)
            )
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    /**
     * Only going to contain the owningKey for passing the checks on the MemberInfo creation side.
     * For the static network we don't need the rotated keys.
     */
    private fun generateIdentityKeys(
        owningKey: String
    ): List<Pair<String, String>> {
        val identityKeys = listOf(owningKey)
        return identityKeys.mapIndexed { index, identityKey ->
            String.format(
                MemberInfoExtension.IDENTITY_KEYS_KEY,
                index
            ) to identityKey
        }
    }

    /**
     * Creates and returns a [SignedMemberInfo] object.
     */
    private fun signMemberInfo(
        memberInfo: MemberInfo,
        memberKey: PublicKey,
        mgmKey: PublicKey,
        signingService: SigningService
    ): SignedMemberInfo {
        val memberContextBF = memberInfo.memberProvidedContext.toWire()
        val mgmContextBF = memberInfo.mgmProvidedContext.toWire()
        return SignedMemberInfo(
            memberContextBF,
            mgmContextBF,
            WireSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(memberKey)),
                ByteBuffer.wrap(
                    signingService.sign(
                        memberKey,
                        memberContextBF.array()
                    ).bytes
                )
            ),
            WireSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(mgmKey)),
                ByteBuffer.wrap(
                    signingService.sign(
                        mgmKey,
                        buildMerkleTree(mgmContextBF, memberContextBF, digestService).hash.bytes
                    ).bytes
                )
            )
        )
    }
}