package net.corda.db.persistence.testkit.components

import java.util.UUID
import javax.sql.DataSource

interface DataSourceAdmin {
    fun getOrCreateDataSource(id: UUID, name: String): DataSource
}
