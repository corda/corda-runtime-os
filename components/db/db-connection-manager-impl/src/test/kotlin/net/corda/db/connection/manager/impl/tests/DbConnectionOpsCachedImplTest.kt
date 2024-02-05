package net.corda.db.connection.manager.impl.tests

import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.connection.manager.impl.DbConnectionOpsCachedImpl
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.UUID
import javax.persistence.EntityManagerFactory

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

    @Test
    fun `when close EMF returned from getOrCreateEntityManagerFactory do nothing`() {
        val emf = mock<EntityManagerFactory>()
        val dbConnectionOps = mock<DbConnectionOps> {
            on { createEntityManagerFactory(any(), any(), any()) } doReturn (emf)
        }
        val dco = DbConnectionOpsCachedImpl(dbConnectionOps, mock())
        val id = UUID.randomUUID()
        val entities = object : JpaEntitiesSet {
            override val persistenceUnitName = "foo"
            override val classes: Set<Class<*>> = setOf(DbConnectionOpsCachedImplTest::class.java)
        }
        val emf2 = dco.getOrCreateEntityManagerFactory(id, entities)
        emf2.close()

        verify(emf, never()).close()
    }
}