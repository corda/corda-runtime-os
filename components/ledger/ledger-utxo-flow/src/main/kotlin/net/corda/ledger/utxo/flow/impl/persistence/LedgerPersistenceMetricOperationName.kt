package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {

    FindGroupParameters,
    FindTransactionWithStatus,
    FindUnconsumedStatesByType,
    FindWithNamedQuery,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistTransactionIfDoesNotExist,
    ResolveStateRefs,
    UpdateTransactionStatus
}