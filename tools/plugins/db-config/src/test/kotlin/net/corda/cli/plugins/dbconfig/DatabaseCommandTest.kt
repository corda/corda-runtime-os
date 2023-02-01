package net.corda.cli.plugins.dbconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseCommandTest {
    @Test
    fun `Verify correct class loader is used for liquibase`() {
        assertThat(DatabaseCommand::class.java.classLoader).isEqualTo(Thread.currentThread().contextClassLoader)
    }
}
