package net.corda.membership.staticnetwork

import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.SigningService
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.conversion.PropertyConverterImpl
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
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.MemberInfoImpl
import net.corda.membership.identity.converter.EndpointInfoConverter
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.MembershipRequestRegistrationResultOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResultOutcome.NOT_SUBMITTED
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant

@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService @Activate constructor(
    @Reference(service = GroupPolicyProvider::class)
    val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = CryptoLibraryFactory::class)
    private val cryptoLibraryFactory: CryptoLibraryFactory,
    @Reference(service = CryptoLibraryClientsFactory::class)
    private val cryptoLibraryClientsFactory: CryptoLibraryClientsFactory
) : MemberRegistrationService, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")

        internal const val DEFAULT_SOFTWARE_VERSION = "5.0.0"
        internal const val DEFAULT_PLATFORM_VERSION = "10"
        internal const val DEFAULT_SERIAL = "1"
    }

    private lateinit var keyEncodingService: KeyEncodingService

    private lateinit var signingService: SigningService

    private var publisher: Publisher? = null

    private lateinit var topic: String

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("StaticMemberRegistrationService started.")
        keyEncodingService = cryptoLibraryFactory.getKeyEncodingService()
        topic = Schemas.Membership.MEMBER_LIST_TOPIC
        // temporary solution until we don't have a more suitable category
        signingService = cryptoLibraryClientsFactory.getSigningService(CryptoCategories.LEDGER)
        publisher = publisherFactory.createPublisher(PublisherConfig("static-member-registration-service"))
        isRunning = true
    }

    override fun stop() {
        logger.info("StaticMemberRegistrationService stopped.")
        isRunning = false
    }

    override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult {
        try {
            val updates = publisher?.publish(
                parseMemberTemplate(member).map {
                    Record(topic, member.id + "-" + HoldingIdentity(it.name.toString(), it.groupId).id, it)
                }
            )
            updates?.forEach { it.get() }
        } catch (e: Exception) {
            logger.warn("Registration failed. Reason: ${e.message}")
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: ${e.message}"
            )
        }
        return MembershipRequestRegistrationResult(SUBMITTED)
    }

    private fun parseMemberTemplate(member: HoldingIdentity): List<MemberInfo> {
        val members = mutableListOf<MemberInfo>()
        val converter = PropertyConverterImpl(
            listOf(
                EndpointInfoConverter(),
                PublicKeyConverter(keyEncodingService),
            )
        )

        val policy = groupPolicyProvider.getGroupPolicy(member)

        val staticMemberList = policy.staticMembers
        if(staticMemberList.isEmpty()) {
            throw IllegalArgumentException("Static member list inside the group policy file cannot be empty.")
        }

        val processedMembers = mutableListOf<MemberX500Name>()
        @Suppress("SpreadOperator")
        staticMemberList.forEach { staticMember ->
            isValidStaticMemberDeclaration(processedMembers, staticMember)
            val owningKey = generateOwningKey(staticMember, policy)
            members.add(
                MemberInfoImpl(
                    memberProvidedContext = MemberContextImpl(
                        sortedMapOf(
                            PARTY_NAME to staticMember[NAME].toString(),
                            PARTY_OWNING_KEY to owningKey,
                            GROUP_ID to policy.groupId,
                            *generateIdentityKeys(owningKey).toTypedArray(),
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
            )
            processedMembers.add(members.last().name)
        }
        return members
    }

    private fun isValidStaticMemberDeclaration(processedMembers: List<MemberX500Name>, member: Map<String, String>) {
        require(member[NAME]!=null || member[NAME]=="" ) { "Member's name is not provided." }
        require(!processedMembers.contains(MemberX500Name.parse(member[NAME]!!))) { "Duplicated static member declaration." }
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
    private fun generateOwningKey(member: Map<String, String>, policy: GroupPolicy): String {
        var keyAlias = member[KEY_ALIAS]
        if(keyAlias==null || keyAlias=="") {
             keyAlias = HoldingIdentity(member[NAME]!!, policy.groupId).id
        }
        val owningKey = signingService.generateKeyPair(keyAlias)
        return keyEncodingService.encodeAsString(owningKey)
    }

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
}