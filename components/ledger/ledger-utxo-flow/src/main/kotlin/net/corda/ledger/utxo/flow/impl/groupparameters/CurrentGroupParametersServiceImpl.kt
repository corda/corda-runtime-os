package net.corda.ledger.utxo.flow.impl.groupparameters

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Suppress("Unused")
@Component(service = [CurrentGroupParametersService::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class CurrentGroupParametersServiceImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider
) : CurrentGroupParametersService, UsedByFlow, SingletonSerializeAsToken {

    override fun get(): SignedGroupParameters {
        val groupReader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
        val signedGroupParameters = requireNotNull(groupReader.signedGroupParameters) {
            "signedGroupParameters could not be accessed."
        }
        requireNotNull(signedGroupParameters.signature) {
            "signedGroupParameters needs to be signed."
        }
        requireNotNull(signedGroupParameters.signatureSpec) {
            "signedGroupParameters needs a signature specification."
        }
        return signedGroupParameters
    }

    // todo CORE-11567 key rotation support needs to be added later
    override fun getMgmKeys(): List<PublicKey> {
        val groupPolicy = groupPolicyProvider.getGroupPolicy(holdingIdentity)
        requireNotNull(groupPolicy) { "Group policy not found for holding identity $holdingIdentity" }
        val mgmInfo = groupPolicy.mgmInfo
        requireNotNull(mgmInfo) { "MGM info is not available in Group policy." }
        val currentMGMKeyEncoded = mgmInfo[PARTY_SESSION_KEYS.format(0)]
        requireNotNull(currentMGMKeyEncoded) { "MGM info does not have first key." }
        val currentMGMKey = keyEncodingService.decodePublicKey(currentMGMKeyEncoded)
        return listOf(currentMGMKey)
    }

    private val holdingIdentity: HoldingIdentity
        get() =
            currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity
}