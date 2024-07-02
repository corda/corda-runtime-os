package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader

/**
 * Provides the functionality to query the corda cluster used in the corda-runtime-plugin-queries group of tasks
 */
class QueryTasksImpl(val pc: ProjectContext) {

    /**
     * Lists out the vnodes to the console
     */
    fun listVNodes() {
        val existingNodes = VirtualNode(pc.restClient).getAllVirtualNodes().virtualNodes
        val cpiNamePadding = 40
        val shortHashPadding = 30
        val x500NamePadding = 60
        val cpiNameTitle = "CPI Name".padEnd(cpiNamePadding)
        val shortHashTitle = "Holding identity short hash".padEnd(shortHashPadding)
        val x500NameTitle = "X500 Name".padEnd(x500NamePadding)
        pc.logger.quiet(cpiNameTitle + shortHashTitle + x500NameTitle)
        existingNodes.forEach {
            val cpiName = it.cpiIdentifier.cpiName.padEnd(cpiNamePadding)
            val shortHash = it.holdingIdentity.shortHash.padEnd(shortHashPadding)
            val x500Name = it.holdingIdentity.x500Name.padEnd(x500NamePadding)
            pc.logger.quiet(cpiName + shortHash + x500Name)
        }
    }

    /**
     * Lists out the uploaded Cpis to the console
     */
    fun listCPIs() {
        val existingCPIs = CpiUploader(pc.restClient).getAllCpis().cpis
        val cpiNamePadding = 40
        val cpiVersionPadding = 20
        val cpiChecksumPadding = 16
        val cpiNameTitle = "CpiName".padEnd(cpiNamePadding)
        val cpiVersionTitle = "CpiVersion".padEnd(cpiVersionPadding)
        val cpiChecksumTitle = "CpiFileCheckSum".padEnd(cpiChecksumPadding)
        pc.logger.quiet(cpiNameTitle + cpiVersionTitle + cpiChecksumTitle)
        existingCPIs.forEach {
            val cpiName = it.id.cpiName.padEnd(cpiNamePadding)
            val cpiVersion = it.id.cpiVersion.padEnd(cpiVersionPadding)
            val cpiChecksum = it.cpiFileChecksum.padEnd(cpiChecksumPadding)
            pc.logger.quiet(cpiName + cpiVersion + cpiChecksum)
        }
    }
}
