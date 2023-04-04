package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version

@Entity
@Table(name = DbSchema.CLUSTER_STATIC_NETWORK_INFO_TABLE)
class StaticNetworkInfoEntity(
    /**
     * The group ID this entity relates to.
     */
    @Id
    @Column(name = "group_id", nullable = false, updatable = false)
    val groupId: String,

    /**
     * The static network's "virtual" MGM's public signing key.
     */
    @Column(name = "mgm_public_signing_key", nullable = false, updatable = false)
    val mgmPublicKey: ByteArray,

    /**
     * The static network's "virtual" MGM's private signing key.
     * The private key is only stored here because static networks are a convenience tool for testing and are not
     * for production usage.
     */
    @Column(name = "mgm_private_signing_key", nullable = false, updatable = false)
    val mgmPrivateKey: ByteArray,

    /**
     * Group parameters serialized as a [KeyValuePairList] using AVRO serialization
     */
    @Column(name = "group_parameters", nullable = false, updatable = true)
    var groupParameters: ByteArray,
) {
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 1
}