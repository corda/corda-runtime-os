package net.corda.cli.commands.dbconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseBootstrapAndUpgradeCommandTest {
    @Test
    fun `Verify correct class loader is used for liquibase`() {
        assertThat(DatabaseBootstrapAndUpgradeCommand::class.java.classLoader).isEqualTo(Thread.currentThread().contextClassLoader)
    }
}
