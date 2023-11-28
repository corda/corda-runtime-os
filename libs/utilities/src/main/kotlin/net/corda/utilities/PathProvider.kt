package net.corda.utilities

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE
import java.nio.file.attribute.PosixFilePermission.GROUP_READ
import java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute

interface PathProvider {
    /**
     * Resolves a directory path and creates the directory if it does not exist.
     */
    fun getOrCreate(config: SmartConfig, vararg dirPath: String): Path
}

@Suppress("SpreadOperator")
class WorkspacePathProvider(
    private val dirResolver: (String, Array<out String>) -> Path = { first, more -> Paths.get(first, *more) }
) : PathProvider {
    private companion object {
        private val WORKSPACE_DIR_PERMISSIONS = asFileAttribute(
            setOf(
                OWNER_READ,
                OWNER_WRITE,
                OWNER_EXECUTE,
                GROUP_READ,
                GROUP_EXECUTE,
                OTHERS_READ,
                OTHERS_EXECUTE
            )
        )
    }

    override fun getOrCreate(config: SmartConfig, vararg dirPath: String): Path {
        require(config.hasPath(ConfigKeys.WORKSPACE_DIR)) {
            "Configuration should not be null for ${ConfigKeys.WORKSPACE_DIR}"
        }

        return dirResolver(config.getString(ConfigKeys.WORKSPACE_DIR)!!, dirPath).also { dir ->
            Files.createDirectories(dir, *dir.posixOptional(WORKSPACE_DIR_PERMISSIONS))
        }
    }
}

@Suppress("SpreadOperator")
class TempPathProvider(
    private val dirResolver: (String, Array<out String>) -> Path = { first, more -> Paths.get(first, *more) }
) : PathProvider {
    private companion object {
        private val TEMP_DIR_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
    }

    override fun getOrCreate(config: SmartConfig, vararg dirPath: String): Path {
        require(config.hasPath(ConfigKeys.TEMP_DIR)) {
            "Configuration should not be null for ${ConfigKeys.TEMP_DIR}"
        }

        return dirResolver(config.getString(ConfigKeys.TEMP_DIR)!!, dirPath).also { dir ->
            Files.createDirectories(dir, *dir.posixOptional(TEMP_DIR_PERMISSIONS))
        }
    }
}
