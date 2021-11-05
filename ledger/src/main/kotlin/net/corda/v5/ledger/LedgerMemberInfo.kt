@file:JvmName("LedgerMemberInfo")
package net.corda.v5.ledger

import net.corda.v5.application.identity.Party
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.conversion.parseOrNull

const val NOTARY_SERVICE_PARTY = "corda.notaryServiceParty"

val MemberInfo.notaryServiceParty: Party?
    get() = memberProvidedContext.parseOrNull(NOTARY_SERVICE_PARTY)