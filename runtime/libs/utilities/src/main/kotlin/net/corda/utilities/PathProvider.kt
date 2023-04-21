package net.corda.utilities

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
    override fun getOrCreate(config: SmartConfig, vararg dirPath: String): Path {
        require(config.hasPath(ConfigKeys.WORKSPACE_DIR)) {
            "Configuration should not be null for ${ConfigKeys.WORKSPACE_DIR}"
        }

        return dirResolver(config.getString(ConfigKeys.WORKSPACE_DIR)!!, dirPath).also {
            Files.createDirectories(it)
        }
    }
}

@Suppress("SpreadOperator")
class TempPathProvider(
    private val dirResolver: (String, Array<out String>) -> Path = { first, more -> Paths.get(first, *more) }
) : PathProvider {
    override fun getOrCreate(config: SmartConfig, vararg dirPath: String): Path {
        require(config.hasPath(ConfigKeys.TEMP_DIR)) {
            "Configuration should not be null for ${ConfigKeys.TEMP_DIR}"
        }

        return dirResolver(config.getString(ConfigKeys.TEMP_DIR)!!, dirPath).also {
            Files.createDirectories(it)
        }
    }
}