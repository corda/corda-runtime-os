package net.corda.libs.configuration.read.kafka.factory

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConfigRepositoryFactoryImplTest {

    private val repositoryFactory = ConfigRepositoryFactoryImpl()

    @Test
    fun testCreateRepository() {
        Assertions.assertNotNull(repositoryFactory.createRepository())
    }
}
