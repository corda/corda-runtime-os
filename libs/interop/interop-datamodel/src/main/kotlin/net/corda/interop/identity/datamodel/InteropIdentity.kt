package net.corda.interop.identity.datamodel

import javax.persistence.Entity
import javax.persistence.Table
import net.corda.db.schema.DbSchema


@Entity
@Table(name = DbSchema.INTEROP_IDENTITY_DB_TABLE)
class InteropIdentity {
    // TODO
}
