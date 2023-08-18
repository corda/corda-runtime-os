package net.corda.libs.virtualnode.datamodel.standaloneentities

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE)
class PersistenceRequestIdEntity(
    @Id
    @Column(name = "request_id", nullable = false)
    var requestId: String
) {
    constructor(): this("")
}