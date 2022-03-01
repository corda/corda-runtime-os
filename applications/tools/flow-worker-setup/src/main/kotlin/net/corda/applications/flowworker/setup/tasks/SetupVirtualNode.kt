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
         * We assume that there is only ever a single CPI and vNode. and publish that
         * against the supplied x500 name.
         */
        val cpis = scanCPIs(repositoryFolder, getTempDir("flow-worker-setup-cpi"))
        if (cpis.size != 1) {
            throw IllegalStateException("Expected to find a single CPI in '${repositoryFolder}'")
        }

        val cpi = cpis.first()
        val x500Name = context.startArgs.x500NName
        val vNode = VirtualNodeInfo(
            HoldingIdentity(x500Name, "1"),
            CpiIdentifier.fromLegacy(cpi.metadata.id))
        val shortId = HoldingIdentity(x500Name, "1").id
        log.info("Published Holding Identity with short ID='${shortId}'")

        /**
         * To support the temporary local package cache used by the sandbox API,
         * we need to publish the cpi dir as a config entry
         */
        val cpiDirConfig: Configuration = if(dockerCpiDir != null){
            Configuration("{ \"cacheDir\": \"${context.startArgs.cpiDockerDir.toString().replace("\\","/")}\"}" , "5.0")
        } else {
            Configuration("{ \"cacheDir\": \"${context.startArgs.cpiDir.toString().replace("\\","/")}\"}" , "5.0")
        }

        val records = listOf(
            Record(Schemas.Config.CONFIG_TOPIC, "corda.cpi", cpiDirConfig),
            Record(VIRTUAL_NODE_INFO_TOPIC, vNode.holdingIdentity.toAvro(), vNode.toAvro()),
            Record(CPI_INFO_TOPIC, cpi.metadata.id.toAvro(), cpi.metadata.toAvro())
        )

        context.publish(records)
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
