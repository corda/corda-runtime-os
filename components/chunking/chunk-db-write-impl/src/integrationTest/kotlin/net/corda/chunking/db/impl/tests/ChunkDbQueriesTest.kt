package net.corda.chunking.db.impl.tests

import com.google.common.jimfs.Jimfs
import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.datamodel.ChunkEntity
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.ChunkDbQueries
import net.corda.chunking.toAvro
import net.corda.data.chunking.Chunk
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.persistence.NoResultException
import javax.persistence.NonUniqueResultException

internal class ChunkDbQueriesTest {
    companion object {
        // N.B.  We're pulling in the config tables as well.
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
        private lateinit var entityManagerFactory: EntityManagerFactory
        private lateinit var queries: ChunkDbQueries

        /**
         * Creates an in-memory database, applies the relevant migration scripts, and initialises
         * [entityManagerFactory].
         */
        @Suppress("Unused")
        @BeforeAll
        @JvmStatic
        private fun prepareDatabase() {
            val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")

            val dbChange = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        DbSchema::class.java.packageName,
                        listOf(MIGRATION_FILE_LOCATION),
                        DbSchema::class.java.classLoader
                    )
                )
            )
            emConfig.dataSource.connection.use { connection ->
                LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
            }
            entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
                "test_unit",
                ChunkingEntities.classes.toList(),
                emConfig
            )
            queries = ChunkDbQueries(entityManagerFactory)
        }


        /** Return the parts we've received - i.e. chunks with non-zero bytes */
        fun partsReceived(entityManagerFactory: EntityManagerFactory, requestId: String): Long {
            return entityManagerFactory.createEntityManager().transaction {
                it.createQuery(
                    "SELECT count(c) FROM ${ChunkEntity::class.simpleName} c " +
                            "WHERE c.requestId = :requestId AND c.data IS NOT NULL"
                )
                    .setParameter("requestId", requestId)
                    .singleResult as Long
            }
        }

        /** Return the expected number of parts - i.e. the part number on the zero bytes chunk */
        fun partsExpected(entityManagerFactory: EntityManagerFactory, requestId: String): Long {
            return entityManagerFactory.createEntityManager().transaction {
                try {
                    (it.createQuery(
                        "SELECT c.partNumber FROM ${ChunkEntity::class.simpleName} c " +
                                "WHERE c.requestId = :requestId and c.data IS NULL"
                    )
                        .setParameter("requestId", requestId)
                        .singleResult as Int).toLong()
                } catch (ex: NoResultException) {
                    0L
                } catch (ex: NonUniqueResultException) {
                    throw ex
                }
            }
        }


        fun randomString() = UUID.randomUUID().toString()

        val loremIpsum = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin id mauris ut tortor 
            condimentum porttitor. Praesent commodo, ipsum vitae malesuada placerat, nisl sem 
            ornare nibh, id rutrum mi elit in metus. Sed ac tincidunt elit. Aliquam quis 
            pellentesque lacus. Quisque commodo tristique pellentesque. Nam sodales, urna id 
            convallis condimentum, nulla lacus vestibulum ipsum, et ultrices sem magna sed neque. 
            Pellentesque id accumsan odio, non interdum nibh. Nullam lacinia vestibulum purus, 
            finibus maximus enim scelerisque eu. Ut nibh lacus, semper eget cursus a, porttitor 
            eu odio. Vivamus vel placerat eros, sed convallis est. Proin tristique ut odio at 
            finibus. 
        """.trimIndent()
    }

    lateinit var fs: FileSystem

    @BeforeEach
    private fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    private fun afterEach() {
        fs.close()
    }

    private fun randomFileName(): Path = fs.getPath(UUID.randomUUID().toString())

    private fun createChunks(someFile: Path): MutableList<Chunk> {
        val tempFile = randomFileName()

        Files.newBufferedWriter(tempFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
            it.write(loremIpsum)
        }

        val divisor = 10
        val chunkSize = loremIpsum.length / divisor
        assertThat(chunkSize * 10)
            .withFailMessage("The test string should not be a multiple of $divisor so that we have a final odd sized chunk ")
            .isNotEqualTo(loremIpsum.length)
        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterFactory.create(chunkSize).apply {
            onChunk { chunks.add(it) }
        }
        // end of setup...

        // This is what we'd write in one of our components
        writer.write(someFile, Files.newInputStream(tempFile))
        return chunks
    }

    @Test
    fun `can write chunks`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        chunks.shuffle()

        chunks.forEach { queries.persist(it) }

        val actual = entityManagerFactory.createEntityManager().transaction {
            it.createQuery("SELECT count(c) FROM ${ChunkEntity::class.simpleName} c WHERE c.requestId = :requestId")
                .setParameter("requestId", requestId)
                .singleResult as Long
        }

        assertThat(actual).isEqualTo(chunks.size.toLong())
    }

    @Test
    fun `write incomplete chunks`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse

        chunks.removeLast()

        var status = AllChunksReceived.NO
        chunks.forEach { status = queries.persist(it) }
        assertThat(status).isEqualTo(AllChunksReceived.NO)
    }

    @Test
    fun `can write chunks to completion`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse

        val last = chunks.last()
        chunks.removeLast()

        var status = AllChunksReceived.NO
        chunks.forEach { status = queries.persist(it) }
        assertThat(status).isEqualTo(AllChunksReceived.NO)

        status = queries.persist(last)
        assertThat(status).isEqualTo(AllChunksReceived.YES)
    }

    @Test
    fun `can write shuffled chunks to completion`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse

        chunks.shuffle()

        val last = chunks.last()
        chunks.removeLast()

        var status = AllChunksReceived.NO
        chunks.forEach { status = queries.persist(it) }
        assertThat(status).isEqualTo(AllChunksReceived.NO)

        status = queries.persist(last)
        assertThat(status).isEqualTo(AllChunksReceived.YES)
    }

    @Test
    fun `can read chunks`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        chunks.shuffle()

        chunks.forEach { queries.persist(it) }

        val actual = entityManagerFactory.createEntityManager().transaction {
            it.createQuery("SELECT count(c) FROM ${ChunkEntity::class.simpleName} c WHERE c.requestId = :requestId")
                .setParameter("requestId", requestId)
                .singleResult as Long
        }
        assertThat(actual).isEqualTo(chunks.size.toLong())

        val partsReceived = partsReceived(entityManagerFactory, requestId)
        assertThat(partsReceived).isEqualTo(chunks.size.toLong() - 1)

        val partsExpected = partsExpected(entityManagerFactory,requestId)
        assertThat(partsExpected).isEqualTo(partsReceived)
    }

    @Test
    fun `check chunks written to db are read with correct sizes`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        // don't shuffle for this.

        var allChunksReceived = AllChunksReceived.NO
        chunks.forEach { allChunksReceived = queries.persist(it) }

        assertThat(allChunksReceived).isEqualTo(AllChunksReceived.YES)

        // In the "real world" we'd probably stream the results because the bytes are multi-MB in size.
        val entities = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(
                "SELECT c FROM ${ChunkEntity::class.simpleName} c " +
                        "WHERE c.requestId = :requestId " +
                        "ORDER BY c.partNumber ASC",
                ChunkEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultList
        }

        assertThat(chunks.size).isEqualTo(entities.size)
        assertThat(chunks.first().requestId).isEqualTo(entities.first().requestId)

        chunks.zip(entities).forEach {
            assertThat(it.first.partNumber).isEqualTo(it.second.partNumber)
            assertThat(it.first.data.capacity()).isEqualTo(it.second.data?.size ?: 0)
            assertThat(it.first.data.limit()).isEqualTo(it.second.data?.size ?: 0)
        }
    }

    @Test
    fun `chunk checksums match`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        // don't shuffle for this.

        chunks.forEach { queries.persist(it) }

        assertThat(queries.checksumIsValid(requestId)).isTrue
    }

    @Test
    fun `broken checksums do not match`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId

        chunks.last().checksum = SecureHash("rubbish", "1234567890".toByteArray()).toAvro()
        chunks.forEach { queries.persist(it) }

        assertThat(queries.checksumIsValid(requestId)).isFalse
    }
}
