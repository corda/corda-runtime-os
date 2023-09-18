
import net.corda.libs.scheduler.datamodel.SchedulerLockImpl
import net.corda.libs.scheduler.datamodel.db.internal.TaskSchedulerLogEntity
import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLogEntityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

class SchedulerLockTest {
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val diffInSeconds = 1234L
    private val loadedLog = TaskSchedulerLogEntity(
        "batman",
        "joker",
        now.minusSeconds(diffInSeconds),
        Date.from(now))
    private val tx = mock<EntityTransaction>()
    private val em = mock<EntityManager>() {
        on { transaction } doReturn tx
    }
    private val repo = mock<TaskSchedulerLogEntityRepository>() {
        on { getOrInitialiseLog(any(), any(), any()) } doReturn loadedLog
    }

    @Test
    fun `when initialise start TX`() {
        SchedulerLockImpl("superman", "hulk", em, repo).use {
            verify(em).transaction
            verify(tx).begin()
        }
    }

    @Test
    fun `when initialise query log`() {
        SchedulerLockImpl("superman", "hulk", em, repo).use {
            verify(repo).getOrInitialiseLog("superman", "hulk", em)
        }
    }

    @Test
    fun `when initialise calculate seconds since last trigger`() {
        SchedulerLockImpl("superman", "hulk", em, repo).use {
            assertThat(it.secondsSinceLastScheduledTrigger).isEqualTo(diffInSeconds)
        }
    }

    @Test
    fun `when updateLog update`() {
        SchedulerLockImpl("superman", "hulk", em, repo).use {
            it.updateLog("thor")
            verify(repo).updateLog("superman", "thor", em)
        }
    }

    @Test
    fun `when close, commit the tx and close the em`() {
        SchedulerLockImpl("superman", "hulk", em, repo).close()
        verify(tx).commit()
        verify(em).close()
    }
}