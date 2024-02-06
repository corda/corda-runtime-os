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
    PersistTransactionIfDoesNotExist,
    PersistTransactionSignatures,
    ResolveStateRefs,
    UpdateTransactionStatus
}
