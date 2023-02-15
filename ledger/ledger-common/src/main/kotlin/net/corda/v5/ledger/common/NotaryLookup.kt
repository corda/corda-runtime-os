package net.corda.v5.ledger.common

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo

/**
 * [NotaryLookup] allows flows to retrieve the [NotaryInfo] in the network.
 *
 * The platform will provide an instance of [NotaryLookup] to flows via property injection.
 */
@DoNotImplement
interface NotaryLookup {

    /**
     * Returns a collection of notary services available on the network.
     *
     * @return A collection of the network's Notary services.
     */
    @get:Suspendable
    val notaryServices: Collection<NotaryInfo>

    /**
     * Looks up the notary information of a notary by legal name.
     *
     * @param notaryServiceName The [MemberX500Name] of the notary service
     * 		to retrieve.
     *
     * @return The [NotaryInfo] that matches the input [notaryServiceName], or null if
     * 		no such notary exists.
     */
    @Suspendable
    fun lookup(notaryServiceName: MemberX500Name): NotaryInfo?

    /**
     * Returns true if and only if the given [virtualNodeName] is a notary, which is
     * defined by the network parameters.
     *
     * @param virtualNodeName The [MemberX500Name] to check.
     *
     * @return true if the input [virtualNodeName] is a notary, or false if it is not
     * 		a notary or does not exist.
     */
    @Suspendable
    fun isNotaryVirtualNode(virtualNodeName: MemberX500Name): Boolean
}
