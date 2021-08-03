package net.corda.v5.application.flows.flowservices

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.DoNotImplement

@DoNotImplement
interface FlowIdentity : CordaFlowInjectable {
    /**
     * Specifies the identity to use for this flow. This will be one of the multiple identities that belong to this node.
     * This is the same as calling `ourIdentityAndCert.party`.
     * @see net.corda.v5.application.node.MemberInfo
     *
     * Note: The current implementation returns the single identity of the node. This will change once multiple identities
     * is implemented.
     */
    val ourIdentity: Party
}
