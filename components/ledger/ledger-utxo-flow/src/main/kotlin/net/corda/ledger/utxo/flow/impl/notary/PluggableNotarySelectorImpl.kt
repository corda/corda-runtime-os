package net.corda.ledger.utxo.flow.impl.notary

import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [PluggableNotarySelector::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class PluggableNotarySelectorImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = NotaryLookup::class)
    private val notaryLookup: NotaryLookup,
) : PluggableNotarySelector, UsedByFlow, SingletonSerializeAsToken {

    // Retrieve notary client plugin class for specified notary service identity. This is done in
    // a non-suspendable function to avoid trying (and failing) to serialize the objects used
    // internally.
    @Suppress("ThrowsCount")
    override fun get(notary: MemberX500Name): PluggableNotaryDetails {
        val notaryInfo = notaryLookup.notaryServices.firstOrNull { it.name == notary }
            ?: throw CordaRuntimeException(
                "Notary service $notary has not been registered on the network."
            )

        val sandboxGroupContext = currentSandboxGroupContext.get()

        val protocolStore =
            sandboxGroupContext.getObjectByKey<FlowProtocolStore>("FLOW_PROTOCOL_STORE") ?: throw CordaRuntimeException(
                "Cannot get flow protocol store for current sandbox group context"
            )

        val flowName = protocolStore.initiatorForProtocol(notaryInfo.protocol, notaryInfo.protocolVersions)

        val flowClass = sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(flowName)

        if (!PluggableNotaryClientFlow::class.java.isAssignableFrom(flowClass)) {
            throw CordaRuntimeException(
                "Notary client flow class $flowName is invalid because " +
                    "it does not inherit from ${PluggableNotaryClientFlow::class.simpleName}."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return PluggableNotaryDetails(
            flowClass as Class<PluggableNotaryClientFlow>,
            notaryInfo.isBackchainRequired
        )
    }
}
