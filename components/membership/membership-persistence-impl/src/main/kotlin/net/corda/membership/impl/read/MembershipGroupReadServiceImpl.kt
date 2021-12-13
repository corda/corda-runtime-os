package net.corda.membership.impl.read

import net.corda.cpiinfo.CpiInfoReader
import net.corda.data.membership.SignedMemberInfo
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.config.MembershipConfig
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.processor.MemberListProcessor
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.membership.read.MembershipGroupReadService
import net.corda.membership.read.MembershipGroupReader
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
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
        const val ACCESSED_TOO_EARLY =
            "Tried to read group data but the MembershipGroupReadService is not running."

        // TEMPORARY VALUES
        const val consumerGroup = "PLACEHOLDER"
        const val memberListTopic = "membership.members"
    }

    /**
     * Subscriptions
     */

    private var memberListSubscription: CompactedSubscription<String, SignedMemberInfo>? = null

    private val subscriptions
        get() = listOf(
            memberListSubscription
        )

    /**
     * Data caches.
     */

    private var _memberListCache: MemberListCache? = null
    private val memberListCache: MemberListCache
        get() = _memberListCache ?: throw CordaRuntimeException(ACCESSED_TOO_EARLY)

    private var _groupReaderCache: MemberDataCache<MembershipGroupReader>? = null
    private val groupReaderCache: MemberDataCache<MembershipGroupReader>
        get() = _groupReaderCache ?: throw CordaRuntimeException(ACCESSED_TOO_EARLY)

    private val caches
        get() = listOf(
            _memberListCache,
            _groupReaderCache
        )

    override val isRunning: Boolean
        get() = subscriptions.all { it?.isRunning ?: false }
                && caches.all { it != null }

    override fun start() {
        createCaches()
        startSubscriptions()
    }

    override fun stop() {
        stopSubscriptions()
        removeCaches()
    }

    override fun getGroupReader(
        groupId: String,
        memberX500Name: MemberX500Name
    ) = groupReaderCache.get(groupId, memberX500Name)
        ?: createGroupReader(groupId, memberX500Name)

    override fun handleConfigEvent(config: MembershipConfig) {
        stop()
        start()
    }

    private fun createGroupReader(
        groupId: String,
        memberX500Name: MemberX500Name
    ): MembershipGroupReader = MembershipGroupReaderImpl(
        groupId,
        memberX500Name,
        getGroupPolicy(groupId, memberX500Name),
        memberListCache
    ).apply {
        groupReaderCache.put(groupId, memberX500Name, this)
    }

    /**
     * Retrieves the GroupPolicy JSON string from the CPI metadata and parses it into a [GroupPolicy] object.
     */
    private fun getGroupPolicy(
        groupId: String,
        memberX500Name: MemberX500Name
    ): GroupPolicy {
        val holdingIdentity = HoldingIdentity(groupId, memberX500Name.toString())
        val groupPolicyJson = virtualNodeInfoReader.get(holdingIdentity)
            ?.cpi
            ?.let { cpiInfoReader.get(it)?.groupPolicy }
        requireNotNull(groupPolicyJson)
        return parseGroupPolicy(groupPolicyJson)
    }

    private fun parseGroupPolicy(groupPolicyJson: String): GroupPolicy {
        // Add group policy parsing call here. Can remove `require` function. Only added to temporarily satisfy
        // compilation.
        require(groupPolicyJson.length >= 0)
        return GroupPolicyImpl(emptyMap())
    }

    private fun createCaches() {
        _memberListCache = MemberListCache.Impl()
        _groupReaderCache = MemberDataCache.Impl()
    }

    private fun removeCaches() {
        _memberListCache = null
        _groupReaderCache = null
    }

    private fun startSubscriptions() {
        memberListSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                consumerGroup,
                memberListTopic
            ),
            MemberListProcessor(
                memberListCache
            )
        ).also {
            it.start()
        }
    }

    private fun stopSubscriptions() = subscriptions.forEach { it?.stop() }
}