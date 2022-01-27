package net.corda.db.schema

import java.util.UUID

/**
 * Corda DB Types
 *
 * When UUID is set, it is the PK for the connection details to be fetched from the cluster DB.
 */
enum class CordaDb(val persistenceUnitName: String, val id: UUID? = null) {
    CordaCluster("corda-cluster"),
    RBAC("corda-rbac", UUID.fromString("fd301442-7ac7-11ec-90d6-0242ac120003")),
    Vault("corda-vault"),
    Crypto("corda-crypto"),
}
