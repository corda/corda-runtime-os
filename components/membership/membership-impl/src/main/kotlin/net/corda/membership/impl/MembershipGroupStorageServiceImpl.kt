package net.corda.membership.impl

import net.corda.membership.MembershipGroupStorageService
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupStorageServiceImpl(
    override val groupId: String,
    override val requestingMember: MemberX500Name
    ) : MembershipGroupStorageService
