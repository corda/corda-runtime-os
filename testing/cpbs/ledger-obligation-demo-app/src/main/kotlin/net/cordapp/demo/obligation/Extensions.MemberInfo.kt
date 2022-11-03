package net.cordapp.demo.utxo

import net.corda.v5.ledger.common.Party
import net.corda.v5.membership.MemberInfo

fun MemberInfo.getParty(index: Int = 0): Party {
    return Party(name, ledgerKeys[index])
}
