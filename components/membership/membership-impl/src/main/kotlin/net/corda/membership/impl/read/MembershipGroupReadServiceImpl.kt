package net.corda.membership.impl.read

import net.corda.cpiinfo.CpiInfoReader
import net.corda.data.membership.SignedMemberInfo
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.config.MembershipConfig
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.impl.read.cache.GroupReaderCache
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.processor.MemberListProcessor
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.membership.read.MembershipGroupReadService
import net.corda.membership.read.MembershipGroupReader
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MembershipGroupReadService::class])
class MembershipGroupReadServiceImpl @Activate constructor(
    @Reference(service = CpiInfoReader::class)
    private val cpiInfoReader: CpiInfoReader,
    @Reference(service = VirtualNodeInfoReaderComponent::class)
    private val virtualNodeInfoReader: VirtualNodeInfoReaderComponent,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
) : MembershipGroupReadService, Lifecycle, MembershipLifecycleComponent {

    private companion object {
        // TEMPORARY VALUES
        const val consumerGroup = "PLACEHOLDER"
        const val memberListTopic = "membership.members"
    }

    private var memberListSubscription: CompactedSubscription<String, SignedMemberInfo>? = null

    /**
     * List of all subscriptions owned by this service
     */
    private val subscriptions
        get() = listOf(
            memberListSubscription
        )

    private var memberListCache: MemberListCache? = null
    private var groupReaderCache: GroupReaderCache? = null

    /**
     * Check all subscriptions are running
     */
    override val isRunning: Boolean
        get() = subscriptions.all { it?.isRunning ?: false }

    override fun start() {
        createCaches()
        startSubscriptions()
    }

    override fun stop() {
        stopSubscriptions()
        closeCaches()
    }

    override fun getGroupReader(
        groupId: String,
        memberX500Name: MemberX500Name
    ): MembershipGroupReader = groupReaderCache?.get(groupId, memberX500Name)
        ?: MembershipGroupReaderImpl(
            groupId,
            memberX500Name,
            getGroupPolicy(groupId, memberX500Name),
            memberListCache!!
        ).also {
            groupReaderCache?.put(groupId, memberX500Name, it)
        }

    /**
     * Retrieves the GroupPolicy JSON string from the CPI metadata and parses it into a [GroupPolicy] object.
     */
    private fun getGroupPolicy(
        groupId: String,
        memberX500Name: MemberX500Name
    ): GroupPolicy {
        val groupPolicyJson = virtualNodeInfoReader.get(
            HoldingIdentity(groupId, memberX500Name.toString())
        )?.cpi?.let { cpiIdentifier ->
            cpiInfoReader.get(cpiIdentifier)?.groupPolicy
        }
        requireNotNull(groupPolicyJson)
        return parseGroupPolicy(groupPolicyJson)
    }

    private fun parseGroupPolicy(groupPolicyJson: String): GroupPolicy {
        // Add group policy parsing call here. Can remove `require` function. Only added to temporarily satisfy
        // compilation.
        require(groupPolicyJson.length >= 0)
        return GroupPolicyImpl(emptyMap())
    }

    override fun handleConfigEvent(config: MembershipConfig) {
        stop()
        // ADD CONFIG HANDLING IMPLEMENTATION HERE
        start()
    }

    private fun createCaches() {
        memberListCache = MemberListCache.Impl()
        groupReaderCache = GroupReaderCache.Impl()
    }

    private fun closeCaches() {
        memberListCache = null
        groupReaderCache = null
    }

    private fun startSubscriptions() {
        memberListSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(consumerGroup, memberListTopic),
            MemberListProcessor(memberListCache!!)
        ).also {
            it.start()
        }
    }

    private fun stopSubscriptions() {
        subscriptions.forEach {
            it?.stop()
        }
    }
}