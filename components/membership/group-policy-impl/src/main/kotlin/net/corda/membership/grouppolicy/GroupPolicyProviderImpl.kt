package net.corda.membership.grouppolicy

import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.factory.GroupPolicyFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.*

@Component(service = [GroupPolicyProvider::class])
class GroupPolicyProviderImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReaderComponent::class)
    private val virtualNodeInfoReader: VirtualNodeInfoReaderComponent,
    @Reference(service = CpiInfoReaderComponent::class)
    private val cpiInfoReader: CpiInfoReaderComponent
) : Lifecycle, GroupPolicyProvider {

    private var virtualNodeInfoCallbackHandle: AutoCloseable? = null
    private val groupPolicyFactory = GroupPolicyFactory()

    private val groupPolicies: MutableMap<HoldingIdentity, GroupPolicy>
        get() = _groupPolicies ?: throw CordaRuntimeException(
            "Tried to access group policy information while the provider service is not running."
        )
    private var _groupPolicies: MutableMap<HoldingIdentity, GroupPolicy>? = null

    override fun getGroupPolicy(
        groupId: String,
        memberX500Name: MemberX500Name
    ): GroupPolicy {
        val holdingIdentity = HoldingIdentity(memberX500Name.toString(), groupId)
        return lookupGroupPolicy(holdingIdentity)
            ?: parseGroupPolicy(holdingIdentity)
    }

    override fun start() {
        _groupPolicies = Collections.synchronizedMap(mutableMapOf())

        /**
         * Register callback so that if a holding identity modifies their virtual node information, the group policy
         * for that holding identity will be parsed in case the virtual node change affected the group policy file.
         */
        virtualNodeInfoCallbackHandle = virtualNodeInfoReader.registerCallback { changed, snapshot ->
            changed.forEach { parseGroupPolicy(it, virtualNodeInfo = snapshot[it]) }
        }
    }

    override fun stop() {
        virtualNodeInfoCallbackHandle?.close()
        _groupPolicies = null
    }

    override val isRunning: Boolean
        get() = _groupPolicies != null && virtualNodeInfoCallbackHandle != null

    private fun lookupGroupPolicy(
        holdingIdentity: HoldingIdentity
    ): GroupPolicy? = groupPolicies[holdingIdentity]

    private fun storeGroupPolicy(
        holdingIdentity: HoldingIdentity,
        groupPolicy: GroupPolicy
    ) {
        groupPolicies[holdingIdentity] = groupPolicy
    }

    /**
     * Parse the group policy string to a [GroupPolicy] object.
     *
     * [VirtualNodeInfoReaderComponent] is used to get the [VirtualNodeInfo], unless provided as a parameter. It may be
     * the case in a virtual node info callback where we are given the changed virtual node info.
     *
     * [CpiInfoReaderComponent] is used to get the CPI metadata containing the group policy for the CPI installed on
     * the virtual node.
     *
     * The group policy is cached to simplify lookups later.
     *
     * @param holdingIdentity The holding identity of the member retrieving the group policy.
     * @param virtualNodeInfo if the VirtualNodeInfo is known, it can be passed in instead of getting this from the
     *  virtual node info reader.
     */
    private fun parseGroupPolicy(
        holdingIdentity: HoldingIdentity,
        virtualNodeInfo: VirtualNodeInfo? = null,
    ): GroupPolicy {
        val vNodeInfo = virtualNodeInfo
            ?: virtualNodeInfoReader.get(holdingIdentity)
        val metadata = vNodeInfo
            ?.cpi
            ?.let { cpiInfoReader.get(it) }
        return groupPolicyFactory
            .createGroupPolicy(metadata?.groupPolicy)
            .apply { storeGroupPolicy(holdingIdentity, this) }
    }
}