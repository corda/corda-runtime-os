package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.db.schema.DbSchema
import java.time.Instant

/**
 * Virtual node database types
 *
 * @property infix String related to DB type that will be used as infix for naming DB related resources
 * @property dbChangeFiles Path to DB changelog master file
 */
enum class VirtualNodeDbType(private val infix: String, val dbChangeFiles: List<String>) {
    /**
     * Virtual node vault database
     */
    VAULT("vault", listOf("net/corda/db/schema/vnode-vault/db.changelog-master.xml")),

    /**
     * Virtual node crypto database
     */
    CRYPTO("crypto", listOf("net/corda/db/schema/vnode-crypto/db.changelog-master.xml"));

    /**
     * Returns DB schema name
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @return schema name for given holding identity ID
     */
    fun getSchemaName(holdingIdentityShortHash: String) = "${DbSchema.VNODE}_${infix}_$holdingIdentityShortHash".lowercase()

    /**
     * Create a DB username for given DB privilege
     * @param dbPrivilege DB privilege
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     */
    fun createUsername(dbPrivilege: DbPrivilege, holdingIdentityShortHash: String) =
        when (dbPrivilege) {
            // NOTE: we add an epoch timestamp here for uniqueness. This should not be important within a Corda
            //  cluster, but in case a shared DB use used, it is possible that multiple DBs will have the same VNodes
            //  (in a test instance, for example)
            DDL -> "vnode_${infix}_${holdingIdentityShortHash}_${Instant.now().epochSecond}_ddl".lowercase()
            DML -> "vnode_${infix}_${holdingIdentityShortHash}_${Instant.now().epochSecond}_dml".lowercase()
        }

    /**
     * Returns DB connection name
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @return DB connection name
     */
    fun getConnectionName(holdingIdentityShortHash: String) = "vnode_${infix}_$holdingIdentityShortHash".lowercase()

    /**
     * Returns DB connection description for given privilege
     * @param dbPrivilege DB privilege
     * @param identity Member's identity (X500 name)
     * @return DB connection description
     */
    fun getConnectionDescription(dbPrivilege: DbPrivilege, identity: String) =
        when (dbPrivilege) {
            DDL -> "$infix DDL connection for $identity"
            DML -> "$infix DML connection for $identity"
        }
}
