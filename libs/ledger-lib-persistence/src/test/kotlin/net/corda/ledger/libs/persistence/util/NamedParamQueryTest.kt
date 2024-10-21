package net.corda.ledger.libs.persistence.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class NamedParamQueryTest {
    @Test
    fun `when no placeholders return`() {
        val sql = "SELECT * FROM foo"

        val query = NamedParamQuery.from(sql)

        assertThat(query.sql).isEqualTo(sql)
    }

    @Test
    fun `when multiple with space placeholders return`() {
        val sql = """
            UPDATE foo
            SET bar= :bar WHERE id = :id)
            RETURNING *
        """.trimIndent()

        val query = NamedParamQuery.from(sql)

        assertSoftly {
            it.assertThat(query.sql).isEqualTo(
                """
                UPDATE foo
                SET bar= ? WHERE id = ?)
                RETURNING *
                """.trimIndent()
            )
            it.assertThat(query.fields.size).isEqualTo(2)
            it.assertThat(query.fields["bar"]).isEqualTo(1)
            it.assertThat(query.fields["id"]).isEqualTo(2)
        }
    }

    @Test
    fun `when multiple with comma placeholders return`() {
        val sql = """
            INSERT INTO foo(id, bar)
            VALUES (:id, :bar)
            RETURNING *
        """.trimIndent()

        val query = NamedParamQuery.from(sql)

        assertSoftly {
            it.assertThat(query.sql).isEqualTo(
                """
                INSERT INTO foo(id, bar)
                VALUES (?, ?)
                RETURNING *
                """.trimIndent()
            )
            it.assertThat(query.fields.size).isEqualTo(2)
            it.assertThat(query.fields["id"]).isEqualTo(1)
            it.assertThat(query.fields["bar"]).isEqualTo(2)
        }
    }

    @Test
    fun `when multiple with placeholder last return`() {
        val sql = "SELECT * FROM foo WHERE id = :id"

        val query = NamedParamQuery.from(sql)

        assertSoftly {
            it.assertThat(query.sql).isEqualTo("SELECT * FROM foo WHERE id = ?")
            it.assertThat(query.fields.size).isEqualTo(1)
            it.assertThat(query.fields["id"]).isEqualTo(1)
        }
    }
}
