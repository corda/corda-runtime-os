package net.corda.db.testkit

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration

class InMemoryEntityManagerConfiguration(dbName: String) : EntityManagerConfiguration {
    private val ds by lazy {
        InMemoryDataSourceFactory().create(dbName)
    }

    override val dataSource: CloseableDataSource
        get() = ds

    override val ddlManage: DdlManage
        get() = DdlManage.UPDATE
}