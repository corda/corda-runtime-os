package net.corda.applications.workers.rpc.utils

import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

private val cordaVersion by lazy {
    val manifest = MemberRegistrationRpcOps::class.java.classLoader
        .getResource("META-INF/MANIFEST.MF")
        ?.openStream()
        ?.use {
            Manifest(it)
        }
    manifest?.mainAttributes?.getValue("Bundle-Version") ?: "5.0.0.0-SNAPSHOT"
}

internal fun E2eCluster.createEmptyJarWithManifest(groupPolicy: ByteArray): ByteArray {
    return ByteArrayOutputStream().use { outputStream ->
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes.putValue("Corda-CPB-Name", uniqueName)
        manifest.mainAttributes.putValue("Corda-CPB-Version", cordaVersion)

        JarOutputStream(outputStream, manifest).use { jarOutputStream ->
            jarOutputStream.putNextEntry(ZipEntry("GroupPolicy.json"))
            jarOutputStream.write(groupPolicy)
            jarOutputStream.closeEntry()
        }
        outputStream.toByteArray()
    }
}