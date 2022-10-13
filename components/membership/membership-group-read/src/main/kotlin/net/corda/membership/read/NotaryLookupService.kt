package net.corda.membership.read

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo
import net.corda.virtualnode.HoldingIdentity
import java.security.PublicKey

/**
 *  Lookup of notary services on the group.
 */
interface NotaryLookupService {
    /**
     * A list of notary services available on the network.
     *
     * Note that the services are sorted based on legal name, and the
     * ordering might change once new notaries are introduced.
     *
     * In case of converting a returned list to a string representation
     * use another delimiter rather than "," or wrap each entry with a
     * postfix and a prefix:
     * ```
     * list.joinToString("|")
     * list.map { "[$it]" }.toString()
     * ```
     * This allows to distinguish between each element of a map, as
     * [MemberX500Name.toString] returns comma separated values of [MemberX500Name].
     *
     * @return A list of the network's Notary services.
     */
    val notaryServices: List<NotaryInfo>

    /**
     * Looks up the notary information of a notary by legal name.
     *
     * @param notaryServiceName The [MemberX500Name] of the notary service
     * 		to retrieve.
     *
     * @return The [NotaryInfo] that matches the input [notaryServiceName], or null if
     * 		no such notary exists.
     */
    fun lookup(notaryServiceName: MemberX500Name): NotaryInfo?

    /**
     * Looks up the notary information of a notary by public key.
     *
     * @param publicKey The [PublicKey] of the notary service to retrieve.
     *
     * @return The [NotaryInfo] that matches the input [publicKey], or null
     * 		if no such notary exists.
     */
    fun lookup(publicKey: PublicKey): NotaryInfo?

    /**
     * Returns true if and only if the given [HoldingIdentity] is a notary, which is
     * defined by the network parameters.
     *
     * @param virtualNodeName The [MemberX500Name] to check.
     *
     * @return true if the input [virtualNodeName] is a notary, or false if it is not
     * 		a notary or does not exist.
     */
    fun isNotary(virtualNodeName: MemberX500Name): Boolean
}