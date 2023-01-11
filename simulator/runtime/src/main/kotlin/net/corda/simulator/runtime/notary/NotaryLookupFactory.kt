package net.corda.simulator.runtime.notary

import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.NotaryInfo

/**
 * Creates a [NotaryLookup] for simulator.
 */
interface NotaryLookupFactory {

    /**
     * @return An implementation of [NotaryLookup].
     */
    fun createNotaryLookup(fiber: SimFiber, notaryInfo: NotaryInfo): NotaryLookup
}