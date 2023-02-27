package net.corda.db.schema;

import org.jetbrains.annotations.NotNull;

/**
 * Corda DB Types
 * <p>
 * {@code persistenceUnitName} also used as ID for the connection in the db_connections table where that is applicable. Not
 * all db types will have such a row with a fixed connection name because they might be defining entities available
 * under multiple schemas named dynamically at run time. Likewise, a db type might have no entities defined under it,
 * where the connection is only used to administer other schemas.
 */
public enum CordaDb {
    CordaCluster("corda-cluster"),
    RBAC("corda-rbac"),
    Uniqueness("corda-uniqueness"),
    Vault("corda-vault"),
    Crypto("corda-crypto"),
    VirtualNodes("corda-virtual-nodes");

    private final String persistenceUnitName;

    CordaDb(@NotNull String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    @NotNull
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }
}
