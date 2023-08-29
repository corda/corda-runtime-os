package net.corda.ledger.persistence.utxo.impl

interface UtxoQueryProvider {
    val findTransactionPrivacySalt: String

    val findTransactionComponentLeafs: String

    val findUnconsumedVisibleStatesByType: String

    val findTransactionSignatures: String

    val findTransactionStatus: String

    val markTransactionVisibleStatesConsumed: String

    val findSignedGroupParameters: String

    val resolveStateRefs: String

    val persistTransaction: String

    val persistTransactionComponentLeaf: String

    val persistTransactionCpk: String

    val persistTransactionOutput: String

    fun persistTransactionVisibleStates(consumed: Boolean): String

    val persistTransactionSignature: String

    val persistTransactionSource: String

    val persistTransactionStatus: String

    val persistSignedGroupParameters: String
}
