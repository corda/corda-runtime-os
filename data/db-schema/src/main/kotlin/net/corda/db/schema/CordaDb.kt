package net.corda.db.schema

/**
 * Corda DB Types
 *
 * [persistenceUnitName] also used as ID for the connection in the db_connections table.
 */
enum class CordaDb(val persistenceUnitName: String) {
    CordaCluster("corda-cluster"),
    RBAC("corda-rbac"),
    Vault("corda-vault"),
    Crypto("corda-crypto"),
}
