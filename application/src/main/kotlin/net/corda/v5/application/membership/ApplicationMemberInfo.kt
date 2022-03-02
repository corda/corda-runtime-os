@file:JvmName("ApplicationMemberInfo")
package net.corda.v5.application.membership

import net.corda.v5.application.identity.Party
import net.corda.v5.base.util.parse
import net.corda.v5.membership.MemberInfo

const val PARTY = "corda.party"

/**
 * Member identity, which includes X.500 name and identity key.
 * Party name is unique within the group and cannot be changed while the membership exists.
 */
val MemberInfo.party: Party
    get() = memberProvidedContext.parse(PARTY)