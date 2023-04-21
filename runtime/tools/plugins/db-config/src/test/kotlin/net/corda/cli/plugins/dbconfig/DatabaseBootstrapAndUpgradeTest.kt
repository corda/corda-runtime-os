package net.corda.cli.plugins.dbconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseBootstrapAndUpgradeTest {
    @Test
    fun `Verify correct class loader is used for liquibase`() {
        assertThat(DatabaseBootstrapAndUpgrade::class.java.classLoader).isEqualTo(Thread.currentThread().contextClassLoader)
    }
}
