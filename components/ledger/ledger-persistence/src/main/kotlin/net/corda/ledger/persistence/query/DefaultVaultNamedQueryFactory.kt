package net.corda.ledger.persistence.query

import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory

/**
 * The default [VaultNamedQueryFactory] that registers queries to support ledger functionality.
 */
interface DefaultVaultNamedQueryFactory : VaultNamedQueryFactory