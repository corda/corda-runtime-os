@file:JvmName("LedgerMemberInfo")
package net.corda.v5.ledger.obsolete

import net.corda.v5.ledger.obsolete.identity.Party
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.membership.MemberInfo

const val NOTARY_SERVICE_PARTY = "corda.notaryServiceParty"

val MemberInfo.notaryServiceParty: Party?
    get() = memberProvidedContext.parseOrNull(NOTARY_SERVICE_PARTY)