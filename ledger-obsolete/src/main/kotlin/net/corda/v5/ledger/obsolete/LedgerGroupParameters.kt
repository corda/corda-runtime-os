@file:JvmName("LedgerGroupParameters")
package net.corda.v5.ledger.obsolete

import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.getValue

const val NOTARIES_KEY = "corda.notaries"

val GroupParameters.notaries: List<NotaryInfo>
    get() = getValue(NOTARIES_KEY)
