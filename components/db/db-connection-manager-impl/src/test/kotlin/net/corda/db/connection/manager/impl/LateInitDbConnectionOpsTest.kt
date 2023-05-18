package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionOps
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class LateInitDbConnectionOpsTest {
    private val delegate = mock<DbConnectionOps>()
    private val ops = LateInitDbConnectionOps().also {
        it.delegate = delegate
    }

    @Test
    fun `getOrCreateEntityManagerFactory calls the delegae`() {
        val connectionId = UUID(30, 1)
        val entitiesSet = mock<JpaEntitiesSet>()
        whenever(delegate.getOrCreateEntityManagerFactory(connectionId, entitiesSet)).doReturn(mock())

        ops.getOrCreateEntityManagerFactory(connectionId, entitiesSet)

        verify(delegate).getOrCreateEntityManagerFactory(connectionId, entitiesSet)
    }
}