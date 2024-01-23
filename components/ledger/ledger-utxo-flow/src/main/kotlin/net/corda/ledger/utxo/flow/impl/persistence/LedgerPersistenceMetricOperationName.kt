package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {

    FindGroupParameters,
    FindSignedLedgerTransactionWithStatus,
    FindTransactionIdsAndStatuses,
    FindTransactionWithStatus,
    FindUnconsumedStatesByType,
    FindWithNamedQuery,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistTransactionIfDoesNotExist,
    ResolveStateRefs,
    UpdateTransactionStatus,
    PersistFilteredTransaction,
    FindFilteredTransaction
}
