import net.corda.db.core.DataSourceFactory
import net.corda.db.core.PostgresDataSourceFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PostgresEntityManagerConfigurationTest {
    @Test
    fun `set default config values`() {
        val dataSourceFactory = mock<DataSourceFactory>() {
            on { create(any(), any(), any(), any(), any(), any()) } doReturn (mock())
        }

        PostgresDataSourceFactory(dataSourceFactory).create(
            "jdbcUrl",
            "user",
            "pass",
            true,
            20
        )

        verify(dataSourceFactory).create(
            eq("org.postgresql.Driver"),
            eq("jdbcUrl"),
            eq("user"),
            eq("pass"),
            eq(true),
            eq(20)
        )
    }
}
