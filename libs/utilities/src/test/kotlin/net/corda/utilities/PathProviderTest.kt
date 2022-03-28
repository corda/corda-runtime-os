package net.corda.utilities

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.FileSystem

@Suppress("SpreadOperator")
class PathProviderTest {
    lateinit var fs: FileSystem
    lateinit var config: SmartConfig

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem(Configuration.unix())

        config = mock()
    }

    @AfterEach
    fun clear() {
        fs.close()
    }

    @Test
    fun `on get or create creates dir if not existing`() {
        whenever(config.hasPath(ConfigKeys.WORKSPACE_DIR)).thenReturn(true)
        whenever(config.getString(ConfigKeys.WORKSPACE_DIR)).thenReturn("/workspace")
        val workspacePathProvider = WorkspacePathProvider { first, more ->
            fs.getPath(first, *more)
        }

        val dir = workspacePathProvider.getOrCreate(config)
        assertTrue(dir.exists())
    }
}