package net.corda.crypto.persistence.db.model

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.orm.EntityManagerConfiguration
import org.junit.jupiter.api.TestInstance
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningKeyEntityTest {
    companion object {
        fun createSigningKeyEntity(
            tenantId: String,
            fullKeyId: String,
            keyId: String,
            timestamp: Instant = Instant.now(),
            category: String = "",
            schemeCodeName: String = "",
            publicKey: ByteArray = byteArrayOf(0x01, 0x02, 0x03),
            keyMaterial: ByteArray? = null,
            encodingVersion: Int? = null,
            masterKeyAlias: String? = null,
            alias: String? = null,
            hsmAlias: String? = null,
            externalId: String? = null,
            hsmId: String = "123",
            status: SigningKeyEntityStatus = SigningKeyEntityStatus.NORMAL
        ): SigningKeyEntity =
            SigningKeyEntity(
                tenantId, fullKeyId, keyId, timestamp, category, schemeCodeName, publicKey, keyMaterial,
                encodingVersion, masterKeyAlias, alias, hsmAlias, externalId, hsmId, status
            )

        fun randomSecureHash(): String =
            SecureHash("SHA-256", ByteArray(32).also { java.util.Random().nextBytes(it) }).toString()

        fun randomShortHash(): String =
            ByteArray(6).also { java.util.Random().nextBytes(it) }.toHexString()
    }


    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("cpi_db")

    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/crypto/db.changelog-master.xml"),
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
        dbConfig.close()
    }

    @Test
    fun `signing keys with same id short key ids can be saved`() {
        // Check clashing short ids can be saved
        val tenantId = randomShortHash()
        val shortKeyId = randomShortHash()
        val signingKeyEntity0 = createSigningKeyEntity(
            tenantId = tenantId,
            fullKeyId = randomSecureHash(),
            keyId = shortKeyId
        )

        val signingKeyEntity1 = createSigningKeyEntity(
            tenantId = tenantId,
            fullKeyId = randomSecureHash(),
            keyId = shortKeyId
        )

        EntityManagerFactoryFactoryImpl().create(
            "test-unit",
            CryptoEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(signingKeyEntity0)
                it.persist(signingKeyEntity1)
                it.flush()
            }
        }
    }
}