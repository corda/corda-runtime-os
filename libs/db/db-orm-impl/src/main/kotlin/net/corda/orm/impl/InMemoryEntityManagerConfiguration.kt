package net.corda.orm.impl

import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import javax.sql.DataSource

class InMemoryEntityManagerConfiguration(dbName: String) : EntityManagerConfiguration {
    private val ds by lazy {
        InMemoryDataSourceFactory().create(dbName)
    }

    override val dataSource: DataSource
        get() = ds

    override val ddlManage: DdlManage
        get() = DdlManage.UPDATE
}

