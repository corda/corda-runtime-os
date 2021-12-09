package net.corda.p2p.deployment

import picocli.CommandLine
import java.util.jar.Manifest

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<out String>? {
        return try {
            ClassLoader.getSystemClassLoader()
                .getResourceAsStream("META-INF/MANIFEST.MF")
                .use {
                    val manifest = Manifest(it)
                    val version = manifest.mainAttributes.getValue("Bundle-Version")
                    if(version == null) {
                        emptyArray()
                    } else {
                        arrayOf(version)
                    }
            }
        } catch (e: Exception) {
            emptyArray()
        }
    }
}