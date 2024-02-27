package net.corda.cli.plugin.initialconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version

class TestSqlFormattersForPropertyAccess {

    @Entity
    @Table(name = "testTable", schema = "testSchema")
    private class TestEntity(
        @Suppress("Unused")
        @get:Column(name = "testyMcTestFace", nullable = false)
        var testFace: String,

        @Suppress("Unused")
        @get:Column(name = "testNumber")
        var testNum: Int,

        @Suppress("Unused")
        @get:Column(name = "testDate")
        var testDate: Instant? = null,

        @Suppress("Unused")
        @get:JoinColumn(name = "crossRef")
        var reference: ReferencedEntity? = null
    )

    @Entity
    @Table(name = "testTable", schema = "testSchema")
    private class VersionedTestEntity(
        @Suppress("Unused")
        @get:Column(name = "testyMcTestFace", nullable = false)
        var testFace: String
    ) {
        @Suppress("Unused")
        @get:Version
        var version: Int = 0
    }

    @Entity
    @Table(name = "testRefTable", schema = "testSchema")
    private class ReferencedEntity(
        @Suppress("Unused")
        @get:Id
        @get:Column(name = "name", nullable = false)
        var name: String
    )

    @Entity
    @Table(name = "testRefTable", schema = "testSchema")
    private class InvalidReferencedEntity(
        @Suppress("Unused")
        @get:JoinColumn(name = "reference")
        var reference: VersionedTestEntity
    )

    @Entity
    @Table(name = "NoSchemaTable")
    private class NoSchemaEntity(
        @Suppress("Unused")
        @get:Column(name = "testface")
        var testface: String
    )

    @Entity
    @Table
    private class NoNameEntity(
        @Suppress("Unused")
        @get:Column(name = "testface")
        var testFace: String
    )

    @Entity
    @Table(name = "UnnamedColumn")
    private class UnnamedColumnEntity(
        @Suppress("Unused")
        @get:Column
        var testFace: String
    )

    @Entity
    @Table(name = "HasValProperty")
    private class EntityWithValProperty(
        @Suppress("Unused")
        @get:Column
        val testFace: String
    )

    @Test
    fun testToInsertStatementNullValueGetsIgnored() {
        val ent = TestEntity("face", 42)
        val statement = ent.toInsertStatement()

        assertEquals(
            "insert into testSchema.testTable (testyMcTestFace, testNumber) " +
                "values ('face', 42);",
            statement
        )
    }

    @Test
    fun testToInsertStatementTimeStamp() {
        val ent = TestEntity("face", 42, Instant.ofEpochSecond(123456))
        val statement = ent.toInsertStatement()

        assertEquals(
            "insert into testSchema.testTable (testDate, testyMcTestFace, testNumber) " +
                "values ('1970-01-02T10:17:36Z', 'face', 42);",
            statement
        )
    }

    @Test
    fun testToInsertStatementJoinColumn() {
        val ent = TestEntity("face", 42, reference = ReferencedEntity("ref#1"))
        val statement = ent.toInsertStatement()

        assertEquals(
            "insert into testSchema.testTable (crossRef, testyMcTestFace, testNumber) " +
                "values ('ref#1', 'face', 42);",
            statement
        )
    }

    @Test
    fun testToInsertStatementVersioned() {
        val ent = VersionedTestEntity("face")
        val statement = ent.toInsertStatement()

        assertEquals(
            "insert into testSchema.testTable (testyMcTestFace, version) " +
                "values ('face', 0);",
            statement
        )
    }

    @Test
    fun testToInsertStatementBobbyTables() {
        val testEntity = TestEntity("Robert'); DROP TABLE Students;--", 34)
        val statement = testEntity.toInsertStatement()

        assertEquals(
            "insert into testSchema.testTable (testyMcTestFace, testNumber) " +
                "values ('Robert\\'); DROP TABLE Students;--', 34);",
            statement
        )
    }

    @Test
    fun joinColumnWithMissingIdBlowsUp() {
        val ent = InvalidReferencedEntity(VersionedTestEntity("foo"))

        val ex = assertThrows<java.lang.IllegalArgumentException> { ent.toInsertStatement() }
        assertEquals(
            "Value " +
                "net.corda.cli.plugin.initialconfig.TestSqlFormattersForPropertyAccess.VersionedTestEntity for join " +
                "column does not have a primary key/id column",
            ex.message
        )
    }

    @Test
    fun testToInsertStatementNoSchema() {
        val ent = NoSchemaEntity("test")
        val statement = ent.toInsertStatement()
        assertEquals("insert into NoSchemaTable (testface) values ('test');", statement)
    }

    @Test
    fun testMissingTableName() {
        val ent = NoNameEntity("test")
        val statement = ent.toInsertStatement()
        assertEquals("insert into NoNameEntity (testface) values ('test');", statement)
    }

    @Test
    fun testMissingColumnName() {
        val ent = UnnamedColumnEntity("test")
        val statement = ent.toInsertStatement()
        assertEquals("insert into UnnamedColumn (testFace) values ('test');", statement)
    }

    @Test
    fun testEntityWithValProperty() {
        val ent = EntityWithValProperty("test")
        val ex = assertThrows<IllegalArgumentException> { ent.toInsertStatement() }
        assertEquals(
            "Property 'val ${EntityWithValProperty::class.java.canonicalName}.testFace: kotlin.String' must be var for JPA annotations.",
            ex.message
        )
    }
}
