package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {

    FindGroupParameters,
    FindSignedLedgerTransactionWithStatus,
    FindTransactionIdsAndStatuses,
    FindTransactionWithStatus,
    FindUnconsumedStatesByType,
    FindWithNamedQuery,
    FindMerkleProofs,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistTransactionIfDoesNotExist,
    PersistTransactionSignatures,
    ResolveStateRefs,
    UpdateTransactionStatus,
    PersistMerkleProofIfDoesNotExist
}
