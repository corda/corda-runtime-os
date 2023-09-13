
import net.corda.libs.scheduler.datamodel.SchedulerLogImpl
import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLogEntityRepository
import net.corda.libs.scheduler.datamodel.db.internal.TaskSchedulerLogEntity
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
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class SchedulerLogTest {
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
    private val emf = mock<EntityManagerFactory>() {
        on { createEntityManager() } doReturn em
    }
    private val repo = mock<TaskSchedulerLogEntityRepository>() {
        on { getOrInitialiseLog(any(), any(), any()) } doReturn loadedLog
    }

    @Test
    fun `when getLastTriggerAndLock return lock with em`() {
        val lock = SchedulerLogImpl(emf, repo).getLastTriggerAndLock("batman", "hulk")

        assertThat(lock).isNotNull
        assertThat(lock.taskName).isEqualTo("batman")
        assertThat(lock.schedulerId).isEqualTo("hulk")

        verify(emf).createEntityManager()
    }
}