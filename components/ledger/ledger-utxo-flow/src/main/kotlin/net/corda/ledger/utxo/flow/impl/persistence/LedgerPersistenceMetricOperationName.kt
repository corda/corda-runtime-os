package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {

    FindGroupParameters,
    FindSignedLedgerTransactionWithStatus,
    FindTransactionWithStatus,
    FindUnconsumedStatesByType,
    FindWithNamedQuery,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistTransactionIfDoesNotExist,
    ResolveStateRefs,
    UpdateTransactionStatus
}