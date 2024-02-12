package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {
    FindFilteredTransactionsAndSignatures,
    FindGroupParameters,
    FindSignedLedgerTransactionWithStatus,
    FindTransactionIdsAndStatuses,
    FindTransactionWithStatus,
    FindUnconsumedStatesByType,
    FindWithNamedQuery,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistFilteredTransaction,
    PersistTransactionIfDoesNotExist,
    PersistTransactionSignatures,
    ResolveStateRefs,
    UpdateTransactionStatus
}
