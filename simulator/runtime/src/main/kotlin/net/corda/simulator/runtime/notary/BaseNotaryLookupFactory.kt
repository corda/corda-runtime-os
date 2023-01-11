package net.corda.simulator.runtime.notary

import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.NotaryInfo
import java.security.PublicKey

class BaseNotaryLookupFactory: NotaryLookupFactory {

    override fun createNotaryLookup(fiber: SimFiber, notaryInfo: NotaryInfo): NotaryLookup {
        return object : NotaryLookup{

            override val notaryServices: Collection<NotaryInfo>
                get() = listOf(notaryInfo)

            override fun isNotaryVirtualNode(virtualNodeName: MemberX500Name): Boolean {
                if(virtualNodeName == notaryInfo.name)
                    return true
                return false
            }

            override fun lookup(notaryServiceName: MemberX500Name): NotaryInfo? {
                if(notaryServiceName == notaryInfo.name)
                    return notaryInfo
                return null
            }
        }
    }
}


data class BaseNotaryInfo(
    override val name: MemberX500Name,
    override val pluginClass: String,
    override val publicKey: PublicKey
):NotaryInfo