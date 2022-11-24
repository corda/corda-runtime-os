package net.corda.membership.read

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo

/**
 *  Lookup of a notary virtual nodes in the group.
 */
interface NotaryVirtualNodeLookup {
    /**
     * Returns the list of [MemberInfo]s representing the virtual nodes
     * for the given notary service identity.
     *
     * @param notaryServiceName The service name of a notary service.
     *
     * @return A list of the notary virtual nodes member information.
     */
    fun getNotaryVirtualNodes(notaryServiceName: MemberX500Name): List<MemberInfo>
}
