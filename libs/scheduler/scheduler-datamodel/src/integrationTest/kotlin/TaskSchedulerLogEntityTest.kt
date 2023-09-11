
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.scheduler.datamodel.SchedulerEntities
import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLogEntityRepository
import net.corda.libs.scheduler.datamodel.db.internal.TASK_SCHEDULER_LOG_GET_QUERY_NAME
import net.corda.libs.scheduler.datamodel.db.internal.TASK_SCHEDULER_LOG_QUERY_PARAM_NAME
import net.corda.libs.scheduler.datamodel.db.internal.TaskSchedulerLogEntity
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskSchedulerLogEntityTest {
    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("scheduler_db")
    private val emf = EntityManagerFactoryFactoryImpl().create(
        "scheduler_db_test",
        SchedulerEntities.classes.toList(),
        dbConfig
    )

    private val logRepository = TaskSchedulerLogEntityRepository()

    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/config/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    @AfterAll
    fun cleanUp() {
        emf.close()
    }

    @Test
    fun `can create TaskSchedulerLogEntity`() {
        val taskId = "foo${UUID.randomUUID()}"
        val log = TaskSchedulerLogEntity(taskId, "bar")

        emf.createEntityManager().transaction {
            it.persist(log)
            it.flush()
        }

        emf.createEntityManager().use { em ->
            val loadedLog = em.find(
                TaskSchedulerLogEntity::class.java,
                taskId
            )

            assertSoftly {
                it.assertThat(loadedLog.name).isEqualTo(taskId)
                it.assertThat(loadedLog.schedulerId).isEqualTo("bar")
                it.assertThat(loadedLog.lastScheduled).isNotNull
            }
        }
    }

    @Test
    fun `can update TaskSchedulerLogEntity`() {
        val taskId = "foo${UUID.randomUUID()}"
        val log = TaskSchedulerLogEntity(taskId, "bar")

        emf.createEntityManager().transaction {
            it.persist(log)
            it.flush()
        }

        val updatedLog = TaskSchedulerLogEntity(taskId, "batman")

        emf.createEntityManager().transaction {
            it.merge(updatedLog)
            it.flush()
        }

        emf.createEntityManager().use { em ->
            val loadedLog = em.find(
                TaskSchedulerLogEntity::class.java,
                taskId
            )

            assertThat(loadedLog.schedulerId).isEqualTo("batman")
        }
    }

    @Test
    fun `TaskSchedulerLogEntity update query sets timestamp and id`() {
        val taskId = "foo${UUID.randomUUID()}"
        val log = TaskSchedulerLogEntity(taskId, "bar")

        emf.createEntityManager().transaction {
            it.persist(log)
            it.flush()
        }

        val createTs = emf.createEntityManager().use { em ->
            em.find(
                TaskSchedulerLogEntity::class.java,
                taskId
            ).lastScheduled
        }

        emf.createEntityManager().transaction {
            logRepository.updateLog(taskId, "batman", it)
        }

        emf.createEntityManager().use { em ->
            val loadedLog = em.find(
                TaskSchedulerLogEntity::class.java,
                taskId
            )

            assertSoftly {
                it.assertThat(loadedLog.schedulerId).isEqualTo("batman")
                it.assertThat(loadedLog.lastScheduled).isAfter(createTs)
            }
        }
    }

    @Test
    fun `get using query returns DB timestamp`() {
        val taskId = "foo${UUID.randomUUID()}"
        val log = TaskSchedulerLogEntity(taskId, "bar")

        emf.createEntityManager().transaction {
            it.persist(log)
            it.flush()
        }

        emf.createEntityManager().transaction { em ->
            val q = em.createNamedQuery(TASK_SCHEDULER_LOG_GET_QUERY_NAME, TaskSchedulerLogEntity::class.java)
            q.setParameter(TASK_SCHEDULER_LOG_QUERY_PARAM_NAME, taskId)
            val loadedLog = logRepository.getOrInitialiseLog(taskId, "superman", em)

            assertSoftly {
                it.assertThat(loadedLog.name).isEqualTo(taskId)
                it.assertThat(loadedLog.schedulerId).isEqualTo("bar")
                it.assertThat(loadedLog.now).isAfter(loadedLog.lastScheduled)
            }
        }
    }

    @Test
    fun `get with pessimistic lock blocks others`() {
        val taskId = "foo${UUID.randomUUID()}"
        val log = TaskSchedulerLogEntity(taskId, "bar")

        emf.createEntityManager().transaction {
            it.persist(log)
            it.flush()
        }

        emf.createEntityManager().use { em ->
            val tx1 = em.transaction
            tx1.begin()
            // load with lock
            val loadedLog1 = logRepository.getOrInitialiseLog(taskId, "superman", em)
            println("Unchanged log date is ${loadedLog1.lastScheduled}")

            // load again on new thread
            var loadedLastScheduled: Instant? = null
            val t = thread(start = true) {
                val loadedLog2 = emf.createEntityManager().transaction { em2 ->
                    logRepository.getOrInitialiseLog(taskId, "the hulk", em2)
                }
                loadedLastScheduled = loadedLog2.lastScheduled
                println("Second thread query complete")
            }

            // wait a second before doing an update
            // I know a sleep in an integration test isn't great, but I'm not sure how else to test this.
            Thread.sleep(1000)
            logRepository.updateLog(taskId, "batman", em)
            println("Update Query complete")

            // read updated row
            val loadedLog3 = logRepository.getOrInitialiseLog(taskId, "superman", em)
            println("Updated log date is ${loadedLog3.lastScheduled}")

            // commit the tx (should release the lock)
            tx1.commit()

            // allow the second thread to complete
            t.join()

            // Second thread should be returning the updated data
            assertThat(loadedLastScheduled).isEqualTo(loadedLog3.lastScheduled)
        }
    }
}