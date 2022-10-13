package net.corda.membership.impl.read.reader

import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.NotaryLookupService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo
import java.security.PublicKey

internal class NotaryLookupServiceImpl(
    private val membershipGroupReader: MembershipGroupReader
) : NotaryLookupService {
    private val notaries
        get() = membershipGroupReader
            .lookup()
            .asSequence()
            .mapNotNull {
                it.notaryDetails
            }

    override val notaryServices: List<NotaryInfo>
        get() = notaries.groupBy {
            it.serviceName
        }.mapValues { (_, detailsList) ->
            detailsList.firstNotNullOfOrNull {
                it.servicePlugin
            }
        }
            .mapNotNull { (serviceName, plugin) ->
                if (plugin == null) {
                    null
                } else {
                    NotaryInfoImpl(
                        serviceName,
                        plugin,
                    )
                }
            }

    override fun lookup(notaryServiceName: MemberX500Name): NotaryInfo? {
        return notaries.filter {
            it.serviceName == notaryServiceName
        }.firstOrNull {
            it.servicePlugin != null
        }?.let {
            NotaryInfoImpl(
                it.serviceName,
                it.servicePlugin!!
            )
        }
    }

    override fun lookup(publicKey: PublicKey): NotaryInfo? {
        val notary = notaries.firstOrNull { details ->
            details.keys.any { key ->
                key.publicKey == publicKey
            }
        } ?: return null
        val serviceName = notary.serviceName
        val plugin = notaries.filter {
            it.serviceName == serviceName
        }.firstNotNullOfOrNull {
            it.servicePlugin
        } ?: return null
        return NotaryInfoImpl(serviceName, plugin)
    }

    override fun isNotary(virtualNodeName: MemberX500Name): Boolean {
        return membershipGroupReader.lookup(virtualNodeName)?.notaryDetails != null
    }
}
