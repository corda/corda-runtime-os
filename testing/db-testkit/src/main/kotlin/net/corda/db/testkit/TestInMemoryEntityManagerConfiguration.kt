package net.corda.db.testkit

import net.corda.orm.DdlManage

class TestInMemoryEntityManagerConfiguration(
    dbName: String,
    override val showSql: Boolean = true
) : InMemoryEntityManagerConfiguration(dbName) {
    override val formatSql: Boolean
        get() = true
    override val ddlManage: DdlManage
        get() = DdlManage.NONE
}
