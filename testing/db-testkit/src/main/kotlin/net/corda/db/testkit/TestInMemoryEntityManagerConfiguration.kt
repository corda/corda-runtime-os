package net.corda.db.testkit

import net.corda.orm.DdlManage

class TestInMemoryEntityManagerConfiguration(dbName: String) :
    InMemoryEntityManagerConfiguration(dbName) {
    override val showSql: Boolean
        get() = true
    override val formatSql: Boolean
        get() = true
    override val ddlManage: DdlManage
        get() = DdlManage.NONE
}