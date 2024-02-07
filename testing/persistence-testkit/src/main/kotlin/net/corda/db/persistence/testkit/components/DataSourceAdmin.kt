package net.corda.db.persistence.testkit.components

import java.util.UUID
import javax.sql.DataSource

interface DataSourceAdmin {
    fun getOrCreateDataSource(id: UUID, name: String): DataSource

    fun createSchemaName(id: UUID, name: String) = "test_${name}_$id".replace("-", "")
}
