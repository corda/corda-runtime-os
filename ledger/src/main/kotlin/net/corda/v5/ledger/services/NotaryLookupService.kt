package net.corda.v5.ledger.services

import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.NotaryInfo.Companion.NOTARY_TYPE_VALIDATING

/**
 *  Lookup of notary services and their types on the network.
 */
@DoNotImplement
interface NotaryLookupService : CordaFlowInjectable, CordaServiceInjectable {
    /**
     * A list of notary services available on the network.
     *
     * Note that the identities are sorted based on legal name, and the ordering might change once new notaries are introduced.
     *
     * In case of converting a returned list to a string representation use another delimiter rather than ","
     * or wrap each entry with a postfix and a prefix:
     * ```
     * list.joinToString("|")
     * list.map { "[$it]" }.toString()
     * ```
     * This allows to distinguish between each element of a map, as [Party.toString] returns comma separated values of [CordaX500Name].
     *
     * @return A list of the network's Notary identities.
     */
    val notaryIdentities: List<Party>

    /**
     * Looks up a well-known identity of notary by legal name.
     *
     * @param name The [CordaX500Name] of the notary to retrieve.
     *
     * @return The [Party] that matches the input [name], or null if no such party exists.
     */
    fun getNotary(name: CordaX500Name): Party?

    /**
     * Returns the list of notary workers for given [notaryService].
     *
     * Notary service is an identity, which is used to notarize transactions. It must be listed in the group parameters, however,
     * it may not have dedicated [MemberInfo] structure. Each notary service must have at least one associated worker.
     *
     * Notary worker is an identity, where notarization requests are routed to. It must have dedicated [MemberInfo] structure, which
     * points to corresponding notary service via notaryServiceParty property.
     *
     * In case of converting a returned list to a string representation use another delimiter rather than ","
     * or wrap each entry with a postfix and a prefix:
     * ```
     * list.joinToString("|")
     * list.map { "[$it]" }.toString()
     * ```
     * This allows to distinguish between each element of a map, as [Party.toString] returns comma separated values of [CordaX500Name].
     *
     * @param notaryService Notary service identity.
     *
     * @return Non-empty list of identities for notary workers.
     *
     * @throws [IllegalStateException] If no workers found for given notary service.
     */
    fun getNotaryWorkers(notaryService: Party): List<Party>

    /**
     * Returns true if and only if the given [Party] is a notary, which is defined by the group parameters.
     *
     * @param party The [Party] to check.
     *
     * @return true if the input [party] is a notary, or false if it is not a notary or does not exist.
     */
    fun isNotary(party: Party): Boolean

    /**
     * Returns the type of the notary.
     *
     * @param party The notary [Party] to check.
     *
     * @return Returns the type of the notary as a string.
     *
     * @throws [UnsupportedOperationException] If the identity is not a notary.
     */
    fun getNotaryType(party: Party): String
}

/**
 * Returns true if and only if the given [Party] is a validating notary.
 *
 * @param party The [Party] to check.
 *
 * @throws [UnsupportedOperationException] If the identity is not a notary.
 *
 * @return true if the input [party] is a validating notary, or false if it is not.
 */
fun NotaryLookupService.isValidating(party: Party) = getNotaryType(party) == NOTARY_TYPE_VALIDATING
