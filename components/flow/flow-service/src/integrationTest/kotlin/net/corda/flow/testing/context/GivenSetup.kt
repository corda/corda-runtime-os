package net.corda.flow.testing.context

import net.corda.data.identity.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

interface GivenSetup : WhenSetup {
    fun virtualNode(cpiId: String, holdingId: HoldingIdentity)

    fun cpkMetadata(cpiId: String, cpkId: String)

    fun sandboxCpk(cpkId: String)

    fun membershipGroupFor(owningMember: HoldingIdentity)

    fun sessionInitiatingIdentity(initiatingIdentity: HoldingIdentity)

    fun sessionInitiatedIdentity(initiatedIdentity: HoldingIdentity)

    val initiatedIdentityMemberName: MemberX500Name
}