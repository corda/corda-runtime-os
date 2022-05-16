package net.corda.crypto.service.impl.hsm.service

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.NOT_FAIL_IF_ASSOCIATION_EXISTS
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_NONE
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_CONFIG_ID
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.createDefaultCryptoConfig
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.softPersistence
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HSMServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var config: SmartConfig
    private lateinit var encryptor: Encryptor
    private lateinit var service: HSMServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        config = createDefaultCryptoConfig(KeyCredentials("passphrase", "salt"))
        encryptor = config.rootEncryptor()
        service = HSMServiceImpl(
            config = config,
            hsmCache = factory.hsmCache,
            schemeMetadata = factory.schemeMetadata,
            opsProxyClient = factory.opsProxyClient
        )
    }

    private fun assert(
        expectedConfigId: String,
        expected: HSMInfo,
        expectedServiceConfig: ByteArray,
        actual: HSMConfig?,
    ) {
        assertNotNull(actual)
        assert(expectedConfigId, expected, actual.info)
        assertArrayEquals(expectedServiceConfig, encryptor.decrypt(actual.serviceConfig))
    }

    private fun assert(
        expectedConfigId: String,
        expected: HSMInfo,
        actual: HSMInfo,
    ) {
        assertEquals(expectedConfigId, actual.id)
        assertEquals(expected.masterKeyAlias, actual.masterKeyAlias)
        assertEquals(expected.masterKeyPolicy, actual.masterKeyPolicy)
        assertEquals(expected.description, actual.description)
        assertEquals(expected.serviceName, actual.serviceName)
        assertEquals(expected.capacity, actual.capacity)
        assertEquals(expected.retries, actual.retries)
        assertEquals(expected.timeoutMills, actual.timeoutMills)
        assertEquals(expected.workerLabel, actual.workerLabel)
        assertEquals(expected.supportedSchemes.size, actual.supportedSchemes.size)
        assertTrue(expected.supportedSchemes.all { actual.supportedSchemes.contains(it) })
    }

    private fun assert(
        expectedConfigId: String,
        expectedTenantId: String,
        expectedHSM: HSMInfo,
        actual: HSMTenantAssociation?
    ) {
        assertNotNull(actual)
        assertEquals(expectedTenantId, actual.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, actual.category)
        assertNotNull(actual.aliasSecret)
        assertNotNull(actual.masterKeyAlias)
        assertThat(factory.softCache.keys).containsKey(actual.masterKeyAlias)
        assert(expectedConfigId, expectedHSM, "{}".toByteArray(), actual.config)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should add new HSM configuration and generate wrapping key when key policy is SHARED for it and then retrieve it`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.SHARED,
            UUID.randomUUID().toString(),
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        assertThat(configId).isNotBlank
        val added = service.findHSMConfig(configId)
        assert(configId, info, serviceConfig, added)
        assertThat(factory.softCache.keys).containsKey(info.masterKeyAlias)
    }

    @Test
    fun `Should add new HSM configuration when key policy is NEW for it and then retrieve it`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        assertThat(configId).isNotBlank
        val added = service.findHSMConfig(configId)
        assert(configId, info, serviceConfig, added)
    }

    @Test
    fun `Should add new HSM configuration when key policy is NONE for it and then retrieve it`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NONE,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        assertThat(configId).isNotBlank
        val added = service.findHSMConfig(configId)
        assert(configId, info, serviceConfig, added)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should update existing HSM configuration and generate wrapping key when updated key policy is SHARED for it and then retrieve it`() {
        val existing = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val existingServiceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(existing, existingServiceConfig)
        assertThat(configId).isNotBlank
        val added = service.findHSMConfig(configId)
        assert(configId, existing, existingServiceConfig, added)
        val updated = HSMInfo(
            configId,
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some updated HSM configuration",
            MasterKeyPolicy.SHARED,
            UUID.randomUUID().toString(),
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val updatedServiceConfig = "{ }".toByteArray()
        val configId2 = service.putHSMConfig(updated, updatedServiceConfig)
        assertEquals(configId, configId2)
        val new = service.findHSMConfig(configId)
        assert(configId, updated, updatedServiceConfig, new)
        assertThat(factory.softCache.keys).containsKey(updated.masterKeyAlias)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `putHSMConfig should throw IllegalArgumentException when key policy is NONE but master key alias is still specified`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NONE,
            UUID.randomUUID().toString(),
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        assertThrows<java.lang.IllegalArgumentException> {
            service.putHSMConfig(info, serviceConfig)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `putHSMConfig should throw IllegalArgumentException when key policy is NEW but master key alias is still specified`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            UUID.randomUUID().toString(),
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        assertThrows<java.lang.IllegalArgumentException> {
            service.putHSMConfig(info, serviceConfig)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `putHSMConfig should throw IllegalArgumentException when key policy is SHARED but master key alias is not specified`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.SHARED,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        assertThrows<java.lang.IllegalArgumentException> {
            service.putHSMConfig(info, serviceConfig)
        }
    }

    @Test
    fun `Should linked categories to HSM then retrieve it`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NONE,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        assertThat(configId).isNotBlank
        val added = service.findHSMConfig(configId)
        assert(configId, info, serviceConfig, added)
        service.linkCategories(
            configId, listOf(
                HSMCategoryInfo(CryptoConsts.Categories.LEDGER, PrivateKeyPolicy.ALIASED),
                HSMCategoryInfo(CryptoConsts.Categories.TLS, PrivateKeyPolicy.WRAPPED)
            )
        )
        val links = service.getLinkedCategories(configId)
        assertThat(links).hasSize(2)
        assertTrue(
            links.any { it.category == CryptoConsts.Categories.LEDGER && it.keyPolicy == PrivateKeyPolicy.ALIASED }
        )
        assertTrue(
            links.any { it.category == CryptoConsts.Categories.TLS && it.keyPolicy == PrivateKeyPolicy.WRAPPED }
        )
    }

    @Test
    fun `Should replace linked categories to HSM then retrieve it`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NONE,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        assertThat(configId).isNotBlank
        val added = service.findHSMConfig(configId)
        assert(configId, info, serviceConfig, added)
        service.linkCategories(
            configId, listOf(
                HSMCategoryInfo(CryptoConsts.Categories.LEDGER, PrivateKeyPolicy.ALIASED),
                HSMCategoryInfo(CryptoConsts.Categories.TLS, PrivateKeyPolicy.WRAPPED)
            )
        )
        val links = service.getLinkedCategories(configId)
        assertThat(links).hasSize(2)
        assertTrue(
            links.any { it.category == CryptoConsts.Categories.LEDGER && it.keyPolicy == PrivateKeyPolicy.ALIASED }
        )
        assertTrue(
            links.any { it.category == CryptoConsts.Categories.TLS && it.keyPolicy == PrivateKeyPolicy.WRAPPED }
        )
        service.linkCategories(
            configId, listOf(
                HSMCategoryInfo(CryptoConsts.Categories.JWT_KEY, PrivateKeyPolicy.BOTH)
            )
        )
        val updatedLinks = service.getLinkedCategories(configId)
        assertThat(updatedLinks).hasSize(1)
        assertTrue(
            updatedLinks.any { it.category == CryptoConsts.Categories.JWT_KEY && it.keyPolicy == PrivateKeyPolicy.BOTH }
        )
    }

    @Test
    @Suppress("MaxLineLength")
    fun `linkCategories should throw IllegalArgumentException when at least one category is blank`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        assertThrows<IllegalArgumentException> {
            service.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(CryptoConsts.Categories.LEDGER, PrivateKeyPolicy.ALIASED),
                    HSMCategoryInfo("", PrivateKeyPolicy.WRAPPED)
                )
            )
        }
    }

    @Test
    fun `linkCategories should throw CryptoServiceLibraryException when HSM config does not exist`() {
        assertThrows<CryptoServiceLibraryException> {
            service.linkCategories(
                UUID.randomUUID().toString(), listOf(
                    HSMCategoryInfo(CryptoConsts.Categories.LEDGER, PrivateKeyPolicy.ALIASED),
                    HSMCategoryInfo(CryptoConsts.Categories.TLS, PrivateKeyPolicy.WRAPPED)
                )
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should assign SOFT HSM with new master key alias and create HSM when id does not exists and then retrieves assignment`() {
        val softConfig = config.softPersistence()
        val tenantId1 = UUID.randomUUID().toString()
        val hsm1 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.LEDGER, emptyMap())
        assertEquals(SOFT_HSM_CONFIG_ID, hsm1.id)
        assertEquals(-1, hsm1.capacity)
        assertEquals(MasterKeyPolicy.NEW, hsm1.masterKeyPolicy)
        assertNull(hsm1.masterKeyAlias)
        assertEquals(softConfig.retries, hsm1.retries)
        assertEquals(softConfig.timeoutMills, hsm1.timeoutMills)
        assertEquals(SOFT_HSM_SERVICE_NAME, hsm1.serviceName)
        val supportedSchemes = SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName }
        assertEquals(supportedSchemes.size, hsm1.supportedSchemes.size)
        assertTrue(supportedSchemes.all { hsm1.supportedSchemes.contains(it) })
        val association1 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assert(SOFT_HSM_CONFIG_ID, tenantId1, hsm1, association1)
        val association11 = service.findAssociation(association1!!.id)
        assert(SOFT_HSM_CONFIG_ID, tenantId1, hsm1, association11)
        val association111 = service.findAssociation(UUID.randomUUID().toString())
        assertNull(association111)
        val hsm2 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.TLS, emptyMap())
        assert(SOFT_HSM_CONFIG_ID, hsm1, hsm2)
        val association2 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertNotNull(association2)
        assertEquals(tenantId1, association2.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association2.category)
        assertNotNull(association2.aliasSecret)
        assertNotNull(association2.masterKeyAlias)
        assertEquals(
            association1.masterKeyAlias,
            association2.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
        assertThat(factory.softCache.keys).containsKey(association2.masterKeyAlias)
        assert(SOFT_HSM_CONFIG_ID, hsm1, "{}".toByteArray(), association2.config)
        // should not fail, just repeats the creating of the wrapping key
        val hsm21 = service.assignSoftHSM(tenantId1, CryptoConsts.Categories.TLS, mapOf(
            NOT_FAIL_IF_ASSOCIATION_EXISTS to "YES"
        ))
        assert(SOFT_HSM_CONFIG_ID, hsm1, hsm21)
        val association21 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertNotNull(association21)
        assertEquals(tenantId1, association21.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association21.category)
        assertNotNull(association21.aliasSecret)
        assertNotNull(association21.masterKeyAlias)
        assertEquals(
            association1.masterKeyAlias,
            association21.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
        assertEquals(association2.masterKeyAlias, association21.masterKeyAlias)
        assert(SOFT_HSM_CONFIG_ID, hsm1, "{}".toByteArray(), association21.config)
        //
        val tenantId2 = UUID.randomUUID().toString()
        val hsm3 = service.assignSoftHSM(tenantId2, CryptoConsts.Categories.TLS, emptyMap())
        assert(SOFT_HSM_CONFIG_ID, hsm1, hsm3)
        val association3 = service.findAssignedHSM(tenantId2, CryptoConsts.Categories.TLS)
        assertNotNull(association3)
        assertEquals(tenantId2, association3.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association3.category)
        assertNotNull(association3.aliasSecret)
        assertNotNull(association3.masterKeyAlias)
        assertNotEquals(
            association1.masterKeyAlias,
            association3.masterKeyAlias,
            "The master key alias must be different for the different tenants"
        )
        assertThat(factory.softCache.keys).containsKey(association3.masterKeyAlias)
        assert(SOFT_HSM_CONFIG_ID, hsm1, "{}".toByteArray(), association3.config)

    }

    @Test
    fun `Should assign HSM with new master key alias and then retrieves assignment`() {
        val info = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig = "{}".toByteArray()
        val configId = service.putHSMConfig(info, serviceConfig)
        service.linkCategories(
            configId,
            CryptoConsts.Categories.all.map {
                HSMCategoryInfo(it, PrivateKeyPolicy.ALIASED)
            }
        )
        val tenantId1 = UUID.randomUUID().toString()
        val hsm1 = service.assignHSM(
            tenantId1, CryptoConsts.Categories.LEDGER, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId, info, hsm1)
        val association1 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.LEDGER)
        assert(configId, tenantId1, hsm1,  association1)
        val hsm2 = service.assignHSM(
            tenantId1, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId, hsm1, hsm2)
        val association2 = service.findAssignedHSM(tenantId1, CryptoConsts.Categories.TLS)
        assertNotNull(association2)
        assertEquals(tenantId1, association2.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association2.category)
        assertNotNull(association2.aliasSecret)
        assertNotNull(association2.masterKeyAlias)
        assertEquals(
            association1!!.masterKeyAlias,
            association2.masterKeyAlias,
            "The master key alias must stay the same for the same tenant, even if categories are different"
        )
        assertThat(factory.softCache.keys).containsKey(association2.masterKeyAlias)
        assert(configId, hsm1, "{}".toByteArray(), association2.config)
        val tenantId2 = UUID.randomUUID().toString()
        val hsm3 = service.assignHSM(
            tenantId2, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId, hsm1, hsm3)
        val association3 = service.findAssignedHSM(tenantId2, CryptoConsts.Categories.TLS)
        assertNotNull(association3)
        assertEquals(tenantId2, association3.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association3.category)
        assertNotNull(association3.aliasSecret)
        assertNotNull(association3.masterKeyAlias)
        assertNotEquals(
            association1.masterKeyAlias,
            association3.masterKeyAlias,
            "The master key alias must be different for the different tenants"
        )
        assertThat(factory.softCache.keys).containsKey(association3.masterKeyAlias)
        assert(configId, hsm1, "{}".toByteArray(), association3.config)

        // next one to the one which has less associated tenants

        val info2 = HSMInfo(
            "",
            Instant.now(),
            UUID.randomUUID().toString(),
            "Some HSM configuration",
            MasterKeyPolicy.NEW,
            null,
            7,
            57,
            SoftCryptoService.produceSupportedSchemes(factory.schemeMetadata).map { it.codeName },
            UUID.randomUUID().toString(),
            42
        )
        val serviceConfig2 = "{}".toByteArray()
        val configId2 = service.putHSMConfig(info2, serviceConfig2)
        service.linkCategories(
            configId2,
            CryptoConsts.Categories.all.map {
                HSMCategoryInfo(it, PrivateKeyPolicy.ALIASED)
            }
        )
        val tenantId3 = UUID.randomUUID().toString()
        val hsm4 = service.assignHSM(
            tenantId3, CryptoConsts.Categories.TLS, mapOf(
                PREFERRED_PRIVATE_KEY_POLICY_KEY to PREFERRED_PRIVATE_KEY_POLICY_NONE
            )
        )
        assert(configId2, info2, hsm4)
        val association4 = service.findAssignedHSM(tenantId3, CryptoConsts.Categories.TLS)
        assertNotNull(association4)
        assertEquals(tenantId3, association4.tenantId)
        assertEquals(CryptoConsts.Categories.TLS, association4.category)
        assertNotNull(association4.aliasSecret)
        assertNotNull(association4.masterKeyAlias)
        assertNotEquals(
            association1.masterKeyAlias,
            association4.masterKeyAlias,
            "The master key alias must be different for the different tenants"
        )
        assertThat(factory.softCache.keys).containsKey(association4.masterKeyAlias)
        assert(configId2, info2, "{}".toByteArray(), association4.config)
    }
}