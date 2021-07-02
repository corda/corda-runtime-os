package net.corda.flow.manager

import net.corda.v5.application.flows.flowservices.dependencies.CordaInjectable
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.application.identity.PartyAndCertificate
import net.corda.v5.application.node.NodeInfo
import net.corda.v5.base.util.NetworkHostAndPort
import java.security.PublicKey

interface NetworkMapCache : CordaInjectable {
    // DOCSTART 1
    /**
     * A list of notary services available on the network.
     *
     * Note that the identities are sorted based on legal name, and the ordering might change once new notaries are introduced.
     */
    val notaryIdentities: List<Party>
    // DOCEND 1

    /**
     * Return a [NodeInfo] which has the given legal name for one of its identities, or null if no such node is found.
     *
     * @throws IllegalArgumentException If more than one matching node is found, in the case of a distributed service identity
     * (such as with a notary cluster). For such a scenerio use [getNodesByLegalName] instead.
     */
    fun getNodeByLegalName(name: CordaX500Name): NodeInfo?

    /**
     * Return a list of [NodeInfo]s which have the given legal name for one of their identities, or an empty list if no
     * such nodes are found.
     *
     * Normally there is at most one node for a legal name, but for distributed service identities (such as with a notary
     * cluster) there can be multiple nodes sharing the same identity.
     */
    fun getNodesByLegalName(name: CordaX500Name): List<NodeInfo>

    /** Look up the node info for a host and port. */
    fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo?

    /**
     * Look up a well known identity (including certificate path) of a legal name. This should be used in preference
     * to well known identity lookup in the identity service where possible, as the network map is the authoritative
     * source of well known identities.
     */
    fun getPeerCertificateByLegalName(name: CordaX500Name): PartyAndCertificate?

    /**
     * Look up the well known identity of a legal name. This should be used in preference
     * to well known identity lookup in the identity service where possible, as the network map is the authoritative
     * source of well known identities.
     */
    fun getPeerByLegalName(name: CordaX500Name): Party? = getPeerCertificateByLegalName(name)?.party

    /** Return all [NodeInfo]s the node currently is aware of (including ourselves). */
    val allNodes: List<NodeInfo>

    /**
     * Look up the node information entries for a specific identity key.
     * Note that normally there will be only one node for a key, but for clusters of nodes or distributed services there
     * can be multiple nodes.
     */
    fun getNodesByLegalIdentityKey(identityKey: PublicKey): List<NodeInfo>

    // DOCSTART 2
    /** Look up a well known identity of notary by legal name. */
    fun getNotary(name: CordaX500Name): Party? = notaryIdentities.firstOrNull { it.name == name }
    // DOCEND 2

    /** Returns true if and only if the given [Party] is a notary, which is defined by the network parameters. */
    fun isNotary(party: Party): Boolean

}
