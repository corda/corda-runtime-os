package net.corda.flow.application.services.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.membership.GroupParametersLookup
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory
import java.security.PublicKey

@Suppress("Unused")
@Component(
    service = [GroupParametersLookup::class, GroupParametersLookupInternal::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class GroupParametersLookupImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider
) : GroupParametersLookup, GroupParametersLookupInternal, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getCurrentGroupParameters(): SignedGroupParameters {
        val groupReader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
        val signedGroupParameters = requireNotNull(groupReader.signedGroupParameters) {
            "signedGroupParameters could not be accessed."
        }
        return signedGroupParameters
    }

    // todo CORE-11567 key rotation support needs to be added later
    override fun getMgmKeys(): List<PublicKey> {
        val groupPolicy = groupPolicyProvider.getGroupPolicy(holdingIdentity)
        requireNotNull(groupPolicy) { "Group policy not found for holding identity $holdingIdentity" }
        val mgmInfo = groupPolicy.mgmInfo
        requireNotNull(mgmInfo) { "MGM info is not available in Group policy." }
        val currentMGMKeyEncoded = mgmInfo[PARTY_SESSION_KEYS_PEM.format(0)] ?:
            mgmInfo[PARTY_SESSION_KEYS.format(0)]
        requireNotNull(currentMGMKeyEncoded) { "MGM info does not have first key." }
        val currentMGMKey = try {
            keyEncodingService.decodePublicKey(currentMGMKeyEncoded)
        } catch (e: Exception) {
            logger.info("Failed to decode public key {}", currentMGMKeyEncoded)
            throw e
        }
        return listOf(currentMGMKey)
    }

    private val holdingIdentity: HoldingIdentity
        get() =
            currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity
}