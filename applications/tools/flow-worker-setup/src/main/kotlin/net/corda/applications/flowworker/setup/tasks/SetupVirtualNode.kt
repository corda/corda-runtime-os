@file:Suppress("UNUSED_VARIABLE")

package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.config.Configuration
import net.corda.libs.packaging.CpiIdentifier
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.schema.Schemas
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

/**
 * This task is used as a temporary utility to publish virtual node, CPI and CPK
 * data to kafka when running independently of the formal components that manage and publish this information.
 */
class SetupVirtualNode(private val context: TaskContext) : Task {

    private companion object {
        val log : Logger = LoggerFactory.getLogger("SetupVirtualNode")
    }

    override fun execute() {

        val repositoryFolder =
            context.startArgs.cpiDir ?: throw IllegalStateException("CPI Directory (--cpiDir) has not been set")

        val dockerCpiDir = context.startArgs.cpiDockerDir

        /**
         * To support the temporary local package cache used by the sandbox API,
         * we need to publish the cpi dir as a config entry
         */
        val configCpiPath = (dockerCpiDir ?: context.startArgs.cpiDir).toString().replace("\\", "/")
        val cpiConfigRecord = Record(
            Schemas.Config.CONFIG_TOPIC,
            "corda.cpi",
            Configuration("{ \"cacheDir\": \"${configCpiPath}\"}", "5.0")
        )


        val cpiList = scanCPIs(repositoryFolder, getTempDir("flow-worker-setup-cpi"))

        val x500Identities = listOf("CN=Bob, O=Bob Corp, L=LDN, C=GB","CN=Alice, O=Alice Corp, L=LDN, C=GB")

        val virtualNodes = cpiList.flatMap { cpi ->
            x500Identities.map { x500 -> cpi to VirtualNodeInfo(HoldingIdentity(x500, cpi.metadata.id.name), CpiIdentifier.fromLegacy(cpi.metadata.id)) }
        }

        virtualNodes.forEach { vNode ->
            val hid = vNode.second.holdingIdentity
            log.info("Create vNode for '${hid.x500Name}'-'${hid.groupId}'  with short ID '${hid.id}'")
        }

        val vNodeCpiRecords = virtualNodes.flatMap {
            listOf(
                Record(VIRTUAL_NODE_INFO_TOPIC, it.second.holdingIdentity.toAvro(), it.second.toAvro()),
                Record(CPI_INFO_TOPIC, it.first.metadata.id.toAvro(), it.first.metadata.toAvro()))
        }

        context.publish(vNodeCpiRecords + cpiConfigRecord)
    }

    private fun scanCPIs(packageRepository: Path, cacheDir: Path): List<CPI> {
        return packageRepository.takeIf(Files::exists)?.let { path ->
            Files.list(path).use { stream ->
                stream.filter {
                    val fileName = it.fileName.toString()
                    CPI.fileExtensions.any(fileName::endsWith)
                }.map {
                    CPI.from(Files.newInputStream(it), cacheDir, it.toString(), true)
                }.toList()
            }
        } ?: listOf()
    }

    private fun getTempDir(dir: String): Path {
        val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), dir)

        if (Files.exists(tmpDir)) {
            Files.list(tmpDir).use { stream ->
                stream.forEach { Files.delete(it) }
            }
        } else {
            Files.createDirectory(tmpDir)
        }

        return tmpDir
    }
}
