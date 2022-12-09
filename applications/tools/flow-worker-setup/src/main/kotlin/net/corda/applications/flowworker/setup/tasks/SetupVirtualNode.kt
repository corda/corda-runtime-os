@file:Suppress("UNUSED_VARIABLE")

package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.toAvro
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.CpiReader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import kotlin.streams.toList

/**
 * This task is used as a temporary utility to publish virtual node, CPI and CPK
 * data to kafka when running independently of the formal components that manage and publish this information.
 */
class SetupVirtualNode(private val context: TaskContext) : Task {

    override fun execute() {
        val repositoryFolder =
            context.startArgs.cpiDir ?: throw IllegalStateException("CPI Directory (--cpiDir) has not been set")

        val cpiList = scanCPIs(repositoryFolder, getTempDir("flow-worker-setup-cpi"))

        val x500Identities = listOf(
            "CN=Bob, O=Bob Corp, L=LDN, C=GB",
            "CN=Alice, O=Alice Corp, L=LDN, C=GB",
            "CN=user1, O=user1 Corp, L=LDN, C=GB",
            "CN=user2, O=user2 Corp, L=LDN, C=GB"
        )

        val virtualNodes = cpiList.flatMap { cpi ->
            x500Identities.map { x500 -> cpi to VirtualNodeInfo(
                HoldingIdentity(MemberX500Name.parse(x500), cpi.metadata.cpiId.name),
                cpi.metadata.cpiId,
                vaultDmlConnectionId = UUID.randomUUID(),
                cryptoDmlConnectionId = UUID.randomUUID(),
                uniquenessDmlConnectionId = UUID.randomUUID(),
                timestamp = Instant.now(),
                state = VirtualNodeInfo.DEFAULT_INITIAL_STATE
            ) }
        }

        virtualNodes.forEach { vNode ->
            val hid = vNode.second.holdingIdentity
            context.log.info("Create vNode for '${hid.x500Name}'-'${hid.groupId}'  with short ID '${hid.shortHash}'")
        }

        cpiList.flatMap { it.cpks }.map { cpk ->
            val cpkChecksum = cpk.metadata.fileChecksum
            val chunkWriter = ChunkWriterFactory.create(972800)
            chunkWriter.onChunk { chunk ->
                val cpkChunkId = CpkChunkId(cpkChecksum.toAvro(), chunk.partNumber)
                context.publish(Record(Schemas.VirtualNode.CPK_FILE_TOPIC, cpkChunkId, chunk))
            }
            chunkWriter.write(cpk.path!!.toString(), Files.readAllBytes(cpk.path!!).inputStream())
        }


        val vNodeCpiRecords = virtualNodes.flatMap {
            listOf(
                Record(VIRTUAL_NODE_INFO_TOPIC, it.second.holdingIdentity.toAvro(), it.second.toAvro()),
                Record(CPI_INFO_TOPIC, it.first.metadata.cpiId.toAvro(), it.first.metadata.toAvro()))
        }

        context.publish(vNodeCpiRecords)
    }

    private fun scanCPIs(packageRepository: Path, cacheDir: Path): List<Cpi> {
        return packageRepository.takeIf(Files::exists)?.let { path ->
            Files.list(path).use { stream ->
                stream.filter {
                    val fileName = it.fileName.toString()
                    Cpi.fileExtensions.any(fileName::endsWith)
                }.map { filePath ->
                    Files.newInputStream(filePath).use { inputStream ->
                        CpiReader.readCpi(inputStream, cacheDir, filePath.toString(), true)
                    }
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
