package net.corda.cli.plugin.initialconfig

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version

class TestSqlFormatters {

    @Entity
    @Table(name = "testtable", schema = "testschema")
    private class TestEntity(
        @Suppress("Unused")
        @Column(name = "testyMcTestFace", nullable = false)
        val testFace: String,

        @Suppress("Unused")
        @Column(name = "testNumber")
        val testNum: Int,

        @Suppress("Unused")
        @Column(name = "testDate")
        val testDate: Instant? = null,

        @Suppress("Unused")
        @JoinColumn(name = "crossRef")
        val reference: ReferencedEntity? = null
    )

    @Entity
    @Table(name = "testtable", schema = "testschema")
    private class VersionedTestEntity(
        @Suppress("Unused")
        @Column(name = "testyMcTestFace", nullable = false)
        val testFace: String
    ) {
        @Suppress("Unused")
        @Version
        var version: Int = 0
    }

    @Entity
    @Table(name = "testRefTable", schema = "testschema")
    private class ReferencedEntity(
        @Suppress("Unused")
        @Id
        @Column(name = "name", nullable = false)
        val name: String
    )

    @Entity
    @Table(name = "testRefTable", schema = "testschema")
    private class InvalidReferencedEntity(
        @Suppress("Unused")
        @JoinColumn(name = "reference")
        val reference: VersionedTestEntity
    )

    @Entity
    @Table(name = "NoSchemaTable")
    private class NoSchemaEntity(
        @Suppress("Unused")
        @Column(name = "testface")
        val testface: String
    )

    @Entity
    @Table
    private class NoNameEntity(
        @Suppress("Unused")
        @Column(name = "testface")
        val testFace: String
    )

    @Entity
    @Table(name = "UnnamedColumn")
    private class UnnamedColumnEntity(
        @Suppress("Unused")
        @Column
        val testFace: String
    )

    @Test
    fun testToInsertStatementNullValueGetsIgnored() {
        val ent = TestEntity("face", 42)
        val statement = ent.toInsertStatement()

        Assertions.assertEquals(
            "insert into testschema.testtable (testyMcTestFace, testNumber) " +
                "values ('face', 42)",
            statement
        )
    }

    @Test
    fun testToInsertStatementTimeStamp() {
        val ent = TestEntity("face", 42, Instant.ofEpochSecond(123456))
        val statement = ent.toInsertStatement()

        Assertions.assertEquals(
            "insert into testschema.testtable (testDate, testyMcTestFace, testNumber) " +
                "values ('1970-01-02T10:17:36Z', 'face', 42)",
            statement
        )
    }

    @Test
    fun testToInsertStatementJoinColumn() {
        val ent = TestEntity("face", 42, reference = ReferencedEntity("ref#1"))
        val statement = ent.toInsertStatement()

        Assertions.assertEquals(
            "insert into testschema.testtable (crossRef, testyMcTestFace, testNumber) " +
                "values ('ref#1', 'face', 42)",
            statement
        )
    }

    @Test
    fun testToInsertStatementVersioned() {
        val ent = VersionedTestEntity("face")
        val statement = ent.toInsertStatement()

        Assertions.assertEquals(
            "insert into testschema.testtable (testyMcTestFace, version) " +
                "values ('face', 0)",
            statement
        )
    }

    @Test
    fun testToInsertStatementBobbyTables() {
        val testEntity = TestEntity("Robert'); DROP TABLE Students;--", 34)
        val statement = testEntity.toInsertStatement()

        Assertions.assertEquals(
            "insert into testschema.testtable (testyMcTestFace, testNumber) " +
                "values ('Robert\\'); DROP TABLE Students;--', 34)",
            statement
        )
    }

    @Test
    fun joinColumnWithMissingIdBlowsUp() {
        val ent = InvalidReferencedEntity(VersionedTestEntity("foo"))

        assertThrows<java.lang.IllegalArgumentException> { ent.toInsertStatement() }.let {
            Assertions.assertEquals(
                "Value " +
                    "net.corda.cli.plugin.initialconfig.TestSqlFormatters.VersionedTestEntity for join " +
                    "column does not have a primary key/id column",
                it.message
            )
        }
    }

    @Test
    fun testToInsertStatementNoSchema() {
        val ent = NoSchemaEntity("test")
        val statement = ent.toInsertStatement()
        Assertions.assertEquals("insert into NoSchemaTable (testface) values ('test')", statement)
    }

    @Test
    fun testMissingTableName() {
        val ent = NoNameEntity("test")
        val statement = ent.toInsertStatement()
        Assertions.assertEquals("insert into NoNameEntity (testface) values ('test')", statement)
    }

    @Test
    fun testMissingColumnName() {
        val ent = UnnamedColumnEntity("test")
        val statement = ent.toInsertStatement()
        Assertions.assertEquals("insert into UnnamedColumn (testFace) values ('test')", statement)
    }
}
