package net.corda.ledger.utxo.flow.impl.notary

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow

interface PluggableNotaryService {

    fun get(notary: MemberX500Name): PluggableNotaryDetails
    fun create(
        transaction: UtxoSignedTransactionInternal,
        pluggableNotaryDetails: PluggableNotaryDetails,
        notarizationType: NotarizationType
    ): PluggableNotaryClientFlow
}
