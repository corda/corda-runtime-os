package net.corda.cli.application

import picocli.CommandLine.IVersionProvider
import java.util.jar.Manifest

/**
 * An abstract class that will read version information out of the plugin manifest.
 *
 * Builds version information using these attributes:
 * - Tool-Name
 * - Tool-Version
 * - Tool-Provider
 * - Tool-Git-Commit
 *
 * To apply, inherit from this class:
 *
 *   class VersionProvider : AbstractCordaCliVersionProvider()
 *
 * Then add to your command annotation:
 *
 *   versionProvider = VersionProvider::class
 */
abstract class AbstractCordaCliVersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> = this.javaClass
        .getResourceAsStream("/META-INF/MANIFEST.MF")
        ?.use {
            Manifest(it).mainAttributes.run {
                arrayOf(
                    "${getValue("Tool-Name")} ${getValue("Tool-Version")}",
                    "Provider: ${getValue("Tool-Provider")}",
                    "Commit ID: ${getValue("Tool-Git-Commit")}"
                )
            }
        } ?: emptyArray()
}