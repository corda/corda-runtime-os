package net.corda.flow.testing.context

import net.corda.flow.testing.tests.HOLDING_IDENTITY_GROUP
import net.corda.v5.base.types.MemberX500Name

interface GivenSetup : WhenSetup {
    fun virtualNode(cpiId: String, holdingIdX500: String, groupId: String = HOLDING_IDENTITY_GROUP)

    fun cpkMetadata(cpiId: String, cpkId: String)

    fun sandboxCpk(cpkId: String)

    fun membershipGroupFor(owningMember: net.corda.data.identity.HoldingIdentity)

    fun sessionInitiatingIdentity(initiatingIdentity: net.corda.data.identity.HoldingIdentity)

    fun sessionInitiatedIdentity(initiatedIdentity: net.corda.data.identity.HoldingIdentity)

    val initiatedIdentityMemberName: MemberX500Name
}