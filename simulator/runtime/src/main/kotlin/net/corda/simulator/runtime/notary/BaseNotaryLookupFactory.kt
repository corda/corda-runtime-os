package net.corda.simulator.runtime.notary

import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.NotaryInfo
import java.security.PublicKey

/**
 * @see [NotaryLookupFactory]
 */
class BaseNotaryLookupFactory: NotaryLookupFactory {

    override fun createNotaryLookup(fiber: SimFiber, notaryInfo: NotaryInfo): NotaryLookup {
        return object : NotaryLookup{

            override fun isNotaryVirtualNode(virtualNodeName: MemberX500Name): Boolean =
                virtualNodeName == notaryInfo.name

            override fun getNotaryServices(): Collection<NotaryInfo> {
                return listOf(notaryInfo)
            }

            override fun lookup(notaryServiceName: MemberX500Name): NotaryInfo? =
                if (notaryServiceName == notaryInfo.name) notaryInfo else null

        }
    }
}

data class BaseNotaryInfo(
    private val name: MemberX500Name,
    private val pluginClass: String,
    private val publicKey: PublicKey
):NotaryInfo {
    override fun getName(): MemberX500Name = name

    override fun getPluginClass(): String = pluginClass

    override fun getPublicKey(): PublicKey = publicKey
}