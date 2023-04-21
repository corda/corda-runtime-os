package net.corda.simulator

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name

/**
 * A holding identity with which to create virtual nodes.
 */
@DoNotImplement
interface HoldingIdentity {

    /**
     * The [MemberX500Name] with which this identity was created.
     */
    val member: MemberX500Name

    /**
     * The [GroupId] with which this identity was created. This will be unique for each instance of Simulator.
     */
    val groupId: String
}