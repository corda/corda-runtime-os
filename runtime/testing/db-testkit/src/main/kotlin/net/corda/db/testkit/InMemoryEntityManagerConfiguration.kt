package net.corda.db.testkit

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

open class InMemoryEntityManagerConfiguration(dbName: String) : EntityManagerConfiguration {
    override val dataSource: CloseableDataSource by lazy(SYNCHRONIZED) {
        // SYNCHRONIZED because we have no opportunity to close any extra
        // candidates that could be created via PUBLICATION mode.
        InMemoryDataSourceFactory().create(dbName)
    }

    override val ddlManage: DdlManage
        get() = DdlManage.UPDATE
}
