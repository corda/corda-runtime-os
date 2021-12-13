package net.corda.ledger.notary

import net.corda.v5.ledger.NotaryInfo
import net.corda.v5.membership.identity.MemberInfo

/**
 *  Lookup of a notary service's workers on the network.
 */
interface NotaryWorkerLookupService {
    /**
     * Returns the list of [MemberInfo]s representing the workers for the given notary service identity.
     *
     * @param notaryService The [NotaryInfo] of a notary service.
     *
     * @return A list of the notary service's workers.
     */
    fun getNotaryWorkers(notaryService: NotaryInfo): List<MemberInfo>
}