package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.db.schema.DbSchema

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
     * @param holdingIdentityId Holding identity ID (short hash)
     * @return schema name for given holding identity ID
     */
    fun getSchemaName(holdingIdentityId: String) = "${DbSchema.VNODE}_${infix}_$holdingIdentityId".lowercase()

    /**
     * Returns DB user for given DB privilege
     * @param dbPrivilege DB privilege
     * @param holdingIdentityId Holding identity ID (short hash)
     * @return DB user for given DB privilege
     */
    fun getUserName(dbPrivilege: DbPrivilege, holdingIdentityId: String) =
        when (dbPrivilege) {
            DDL -> "vnode_${infix}_${holdingIdentityId}_ddl".lowercase()
            DML -> "vnode_${infix}_${holdingIdentityId}_dml".lowercase()
        }

    /**
     * Returns DB connection name
     * @param holdingIdentityId Holding identity ID (short hash)
     * @return DB connection name
     */
    fun getConnectionName(holdingIdentityId: String) = "vnode_${infix}_$holdingIdentityId".lowercase()

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
