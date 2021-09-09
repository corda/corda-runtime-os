@file:JvmName("LedgerNetworkParameters")
package net.corda.v5.ledger

import net.corda.v5.application.node.NetworkParameters
import net.corda.v5.application.node.getValue

const val NOTARIES_KEY = "corda.notaries"

val NetworkParameters.notaries: List<NotaryInfo>
    get() = getValue(NOTARIES_KEY)
