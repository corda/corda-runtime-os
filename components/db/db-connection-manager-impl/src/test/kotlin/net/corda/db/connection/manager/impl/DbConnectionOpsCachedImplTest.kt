package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionOps
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID
import javax.persistence.EntityManagerFactory

class DbConnectionOpsCachedImplTest {
    private val factory = mock<EntityManagerFactory>()
    private val delegate = mock<DbConnectionOps> {
        on { createEntityManagerFactory(any(), any()) } doReturn factory
    }
    private val entitiesRegistry = mock<JpaEntitiesRegistry>()
    private val cache = DbConnectionOpsCachedImpl(
        delegate,
        entitiesRegistry,
    )

    @Nested
    inner class GetOrCreateEntityManagerFactoryTests {
        @Test
        fun `it return a new entity manager factory if no one exists`() {
            val createdFactory = cache.getOrCreateEntityManagerFactory(UUID(0,2), mock())

            assertThat(createdFactory).isSameAs(factory)
        }

        @Test
        fun `it calls the delegate only once`() {
            val entitiesSet = mock<JpaEntitiesSet>()
            val uuid = UUID(1,2)
            cache.getOrCreateEntityManagerFactory(uuid, entitiesSet)

            cache.getOrCreateEntityManagerFactory(uuid, entitiesSet)

            verify(delegate, times(1)).createEntityManagerFactory(uuid, entitiesSet)
        }
    }
}