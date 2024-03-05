package net.corda.ledger.utxo.flow.impl.notary

import net.corda.v5.base.types.MemberX500Name

interface PluggableNotarySelector {

    fun get(notary: MemberX500Name): PluggableNotaryDetails
}