package net.corda.utilities

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE
import java.nio.file.attribute.PosixFilePermission.GROUP_READ
import java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE

@Suppress("SpreadOperator")
class PathProviderTest {
    private lateinit var fs: FileSystem
    private lateinit var config: SmartConfig

    @AfterEach
    fun clear() {
        fs.close()
    }

    @Nested
    @DisplayName("PathProvider with UNIX filesystem")
    inner class Unix {
        @BeforeEach
        fun setUp() {
            val posix = Configuration.unix().toBuilder()
                .setAttributeViews("basic", "posix")
                .build()
            fs = Jimfs.newFileSystem(posix)

            config = mock()
        }

        @Test
        fun `getOrCreate creates workspace dir if not existing`() {
            whenever(config.hasPath(ConfigKeys.WORKSPACE_DIR)).thenReturn(true)
            whenever(config.getString(ConfigKeys.WORKSPACE_DIR)).thenReturn("/workspace")
            val workspacePathProvider = WorkspacePathProvider { first, more ->
                fs.getPath(first, *more)
            }

            val dir = workspacePathProvider.getOrCreate(config, "subdir")
            assertThat(dir).hasToString("/workspace/subdir")
                .isDirectory
                .isReadable
                .isWritable
                .isExecutable
            assertThat(Files.getPosixFilePermissions(dir))
                .containsExactlyInAnyOrder(
                    OWNER_WRITE,
                    OWNER_READ,
                    OWNER_EXECUTE,
                    GROUP_READ,
                    GROUP_EXECUTE,
                    OTHERS_EXECUTE,
                    OTHERS_READ
                )
            verify(config).hasPath(ConfigKeys.WORKSPACE_DIR)
            verify(config).getString(ConfigKeys.WORKSPACE_DIR)
        }

        @Test
        fun `getOrCreate creates temporary dir if not existing`() {
            whenever(config.hasPath(ConfigKeys.TEMP_DIR)).thenReturn(true)
            whenever(config.getString(ConfigKeys.TEMP_DIR)).thenReturn("/tmp")
            val temporaryPathProvider = TempPathProvider { first, more ->
                fs.getPath(first, *more)
            }

            val dir = temporaryPathProvider.getOrCreate(config, "subdir")
            assertThat(dir).hasToString("/tmp/subdir")
                .isDirectory
                .isReadable
                .isWritable
                .isExecutable
            assertThat(Files.getPosixFilePermissions(dir))
                .containsExactlyInAnyOrder(OWNER_WRITE, OWNER_READ, OWNER_EXECUTE)
            verify(config).hasPath(ConfigKeys.TEMP_DIR)
            verify(config).getString(ConfigKeys.TEMP_DIR)
        }
    }

    @Nested
    @DisplayName("PathProvider with Windows filesystem")
    inner class Windows {
        @BeforeEach
        fun setUp() {
            fs = Jimfs.newFileSystem(Configuration.windows())
            config = mock()
        }

        @Test
        fun `getOrCreate creates workspace dir if not existing`() {
            whenever(config.hasPath(ConfigKeys.WORKSPACE_DIR)).thenReturn(true)
            whenever(config.getString(ConfigKeys.WORKSPACE_DIR)).thenReturn("c:\\workspace")
            val workspacePathProvider = WorkspacePathProvider { first, more ->
                fs.getPath(first, *more)
            }

            val dir = workspacePathProvider.getOrCreate(config, "subdir")
            assertThat(dir).hasToString("c:\\workspace\\subdir")
                .isDirectory
                .isReadable
                .isWritable
                .isExecutable
            verify(config).hasPath(ConfigKeys.WORKSPACE_DIR)
            verify(config).getString(ConfigKeys.WORKSPACE_DIR)
        }

        @Test
        fun `getOrCreate creates temporary dir if not existing`() {
            whenever(config.hasPath(ConfigKeys.TEMP_DIR)).thenReturn(true)
            whenever(config.getString(ConfigKeys.TEMP_DIR)).thenReturn("c:\\tmp")
            val temporaryPathProvider = TempPathProvider { first, more ->
                fs.getPath(first, *more)
            }

            val dir = temporaryPathProvider.getOrCreate(config, "subdir")
            assertThat(dir).hasToString("c:\\tmp\\subdir")
                .isDirectory
                .isReadable
                .isWritable
                .isExecutable
            verify(config).hasPath(ConfigKeys.TEMP_DIR)
            verify(config).getString(ConfigKeys.TEMP_DIR)
        }
    }
}
