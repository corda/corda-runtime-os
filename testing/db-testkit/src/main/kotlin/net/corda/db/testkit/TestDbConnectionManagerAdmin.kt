package net.corda.db.testkit

import java.util.*
import javax.sql.DataSource

interface TestDbConnectionManagerAdmin {
    fun getOrCreateDataSource(id: UUID, name: String): DataSource
}