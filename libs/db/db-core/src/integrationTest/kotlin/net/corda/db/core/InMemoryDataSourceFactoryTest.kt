package net.corda.db.core

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import java.sql.SQLException

class InMemoryDataSourceFactoryTest {
    @Test
    fun `can close DataSource`() {
        val dataSourceFactory = InMemoryDataSourceFactory()
        val closeableDataSource = dataSourceFactory.create("test")
        val connection = closeableDataSource.connection
        Assertions.assertThat(connection).isNotNull
        Assertions.assertThat(connection.isClosed).isFalse
        closeableDataSource.close()
        assertThatExceptionOfType(SQLException::class.java)
            .isThrownBy {
                closeableDataSource.connection
            }.withMessageContaining("has been closed")
    }
}
