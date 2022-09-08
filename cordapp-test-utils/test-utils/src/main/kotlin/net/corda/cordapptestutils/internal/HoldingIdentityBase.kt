package net.corda.cordapptestutils.internal

import net.corda.cordapptestutils.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

data class HoldingIdentityBase(override val member: MemberX500Name) : HoldingIdentity