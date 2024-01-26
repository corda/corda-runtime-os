package net.corda.orm.impl

import net.corda.db.testkit.dbutilsimpl.PostgresHelper
import net.corda.orm.impl.test.entities.Owner
import net.corda.orm.utils.use
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DefaultSchemaTest {
    @Test
    fun `can persist JPA entity`() {
        val conf = PostgresHelper().getEntityManagerConfiguration("DefaultSchemaTest")
        val schema = "custom_schema_${Instant.now().epochSecond}"
        conf.dataSource.connection.use {
            it.autoCommit = true
            it.createStatement().execute("CREATE SCHEMA $schema")
            it.createStatement().execute("CREATE TABLE $schema.owner(age INT, name VARCHAR, id UUID)")
        }

        val emf = EntityManagerFactoryFactoryImpl().create(
            "DefaultSchemaTest",
            listOf(Owner::class.java),
            conf,
            schema
        )

        val ownerId = UUID.randomUUID()
        val owner = Owner(ownerId, "Fred", 25)

        emf.createEntityManager().use {
            it.transaction.begin()
            it.persist(owner)
            it.transaction.commit()
        }

        val fetch = conf.dataSource.connection.use { con ->
            con.prepareStatement("SELECT name FROM $schema.owner WHERE id = '$ownerId'").use {
                val r = it.executeQuery()
                r.next()
                r.getString(1)
            }
        }

        assertThat(fetch).isEqualTo("Fred")
    }
}