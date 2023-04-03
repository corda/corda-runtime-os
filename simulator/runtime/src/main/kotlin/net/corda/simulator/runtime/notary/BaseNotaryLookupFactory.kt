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

/**
 * Holds the notary info for simulated notary
 */
data class BaseNotaryInfo(
    private val name: MemberX500Name,
    private val protocol: String,
    private val protocolVersions: Collection<Int>,
    private val publicKey: PublicKey
):NotaryInfo {
    override fun getName(): MemberX500Name = name

    override fun getProtocolVersions(): Collection<Int> = protocolVersions

    override fun getProtocol(): String = protocol

    override fun getPublicKey(): PublicKey = publicKey
}