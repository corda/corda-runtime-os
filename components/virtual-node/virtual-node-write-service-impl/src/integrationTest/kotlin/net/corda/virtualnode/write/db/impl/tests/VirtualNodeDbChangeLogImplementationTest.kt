package net.corda.virtualnode.write.db.impl.tests

import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class VirtualNodeDbChangeLogImplementationTest  {
    // It hardly seems worth bringing in a template system for one expansion, so we'll just do string replacement on _INCLUDETARGET_
    val primaryTemplate = """<?xml version="1.1" encoding="UTF-8" standalone="no"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">
    _INCLUDETARGET_
</databaseChangeLog>
"""
    val secondaryContent = """<?xml version="1.1" encoding="UTF-8" standalone="no"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet author="R3.Corda" id="dogs-migrations-v1.0">
        <createTable tableName="dog">
            <column name="id" type="uuid">
                <constraints nullable="false"/>
            </column>
            <column name="name" type="VARCHAR(255)"/>
            <column name="birthdate" type="DATETIME"/>
            <column name="owner" type="VARCHAR(255)"/>
        </createTable>
        <addPrimaryKey columnNames="id" constraintName="dog_id" tableName="dog"/>
    </changeSet>
</databaseChangeLog>
"""
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val dbConfig = DbUtils.getEntityManagerConfiguration("test")
        private val fakeId = UUID.randomUUID()
    }

    private final fun doMigration(includeElement: String, failureText: String? = null) {
        dbConfig.dataSource.connection.use {
                it.createStatement().execute("DROP TABLE IF EXISTS dog")
                it.commit()
            }
        val lbm = LiquibaseSchemaMigratorImpl()
        val primaryContent = primaryTemplate.replace("_INCLUDETARGET_", includeElement)
        val primary = CpkDbChangeLog("migration/db.changelog-master.xml", primaryContent, SecureHash("SHA1","abc".toByteArray()))
        val secondary = CpkDbChangeLog("migration/dogs-migration-v1.0.xml", secondaryContent, SecureHash("SHA1","abc".toByteArray()))
        val cl = VirtualNodeDbChangeLog(listOf(primary, secondary))
        assertThat(cl.masterChangeLogFiles.size).isEqualTo(1)
        if (failureText != null) {
            assertThatThrownBy {
                lbm.updateDb(dbConfig.dataSource.connection, cl)
            }.hasMessageContaining(failureText)
        } else {
            lbm.updateDb( dbConfig.dataSource.connection, cl)
        }
    }
    @Test
    fun `Liquibase migration with included absolute filename`() = doMigration("<include file=\"migration/dogs-migration-v1.0.xml\" />")

    @Test
    fun `Liquibase migration with include dot slash fails`() = doMigration("<include file=\"./dogs-migration-v1.0.xml\" />", "Cannot find changelog file")

    @Test
    fun `Liquibase migration with plain include fails`() = doMigration("<include file=\"dogs-migration-v1.0.xml\" />", "Cannot find changelog file")
}