package net.corda.db.testkit

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration

open class InMemoryEntityManagerConfiguration(dbName: String) : EntityManagerConfiguration {
    override val dataSource: CloseableDataSource by lazy {
        InMemoryDataSourceFactory().create(dbName)
    }

    override val ddlManage: DdlManage
        get() = DdlManage.UPDATE
}
