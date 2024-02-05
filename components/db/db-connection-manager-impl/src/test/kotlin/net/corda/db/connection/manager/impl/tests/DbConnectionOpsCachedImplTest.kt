package net.corda.db.connection.manager.impl.tests

import net.corda.db.connection.manager.impl.DbConnectionOpsCachedImpl
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.util.UUID

class DbConnectionOpsCachedImplTest {
    @Test
    fun `when getOrCreateEntityManagerFactory reject if called with existing set`() {
        val dco = DbConnectionOpsCachedImpl(mock(), mock())
        val id = UUID.randomUUID()
        val entities = object : JpaEntitiesSet {
            override val persistenceUnitName = "foo"
            override val classes: Set<Class<*>> = setOf(DbConnectionOpsCachedImplTest::class.java)
        }
        dco.getOrCreateEntityManagerFactory(id, entities)

        val entities2 = object : JpaEntitiesSet {
            override val persistenceUnitName = "foo"
            override val classes: Set<Class<*>> = entities.classes.plus(DbConnectionOpsCachedImpl::class.java)
        }

        assertThrows<IllegalArgumentException> {
            dco.getOrCreateEntityManagerFactory(id, entities2)
        }
    }
}