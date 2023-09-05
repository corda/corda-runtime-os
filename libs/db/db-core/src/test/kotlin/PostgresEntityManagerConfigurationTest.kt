import net.corda.db.core.DBBaseDataSourceFactory
import net.corda.db.core.DataSourceFactory
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
            on { create(any(), any(), any(), any(), any(), any(),any(), any(), any(), any(), any(), any()) } doReturn (mock())
        }

            DBBaseDataSourceFactory(dataSourceFactory).create(
                "org.postgresql.Driver",
            "jdbcUrl",
            "user",
            "pass",
        )

        verify(dataSourceFactory).create(
            eq("org.postgresql.Driver"),
            eq("jdbcUrl"),
            eq("user"),
            eq("pass"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }
}
