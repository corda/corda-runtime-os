package net.corda.gradle.plugin.queries

import kong.unirest.Unirest
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.getExistingNodes
import net.corda.gradle.plugin.getUploadedCpis

/**
 * Provides the functionality to query the corda cluster used in the csde-queries group of tasks
 */
class CordaQueriesHelper(val pc: ProjectContext) {

    init {
        Unirest.config().verifySsl(false)
    }

    /**
     * Lists out the vnodes to the console
     */
    fun listVNodes() {
        val vNodes= getExistingNodes(pc)
        val cpiNamePadding = 40
        val shortHashPadding = 30
        val x500NamePadding = 60
        val cpiNameTitle = "CPI Name".padEnd(cpiNamePadding)
        val shortHashTitle = "Holding identity short hash".padEnd(shortHashPadding)
        val x500NameTitle = "X500 Name".padEnd(x500NamePadding)
        pc.logger.quiet(cpiNameTitle + shortHashTitle + x500NameTitle)
        vNodes.forEach {
            val cpiName = it.cpiIdentifier?.cpiName?.padEnd(cpiNamePadding)
            val shortHash = it.holdingIdentity?.shortHash?.padEnd(shortHashPadding)
            val x500Name = it.holdingIdentity?.x500Name?.padEnd(x500NamePadding)
            pc.logger.quiet(cpiName + shortHash + x500Name)
        }
    }

    /**
     * Lists out the uploaded Cpis to the console
     */
    fun listCPIs() {
        val cpis = getUploadedCpis(pc)
        val cpiNamePadding = 40
        val cpiVersionPadding = 20
        val cpiChecksumPadding = 16
        val cpiNameTitle = "CpiName".padEnd(cpiNamePadding)
        val cpiVersionTitle = "CpiVersion".padEnd(cpiVersionPadding)
        val cpiChecksumTitle = "CpiFileCheckSum".padEnd(cpiChecksumPadding)
        pc.logger.quiet(cpiNameTitle + cpiVersionTitle + cpiChecksumTitle)
        cpis.forEach {
            val cpiName = it.id?.cpiName?.padEnd(cpiNamePadding)
            val cpiVersion = it.id?.cpiVersion?.padEnd(cpiVersionPadding)
            val cpiChecksum = it.cpiFileChecksum?.padEnd(cpiChecksumPadding)
            pc.logger.quiet(cpiName + cpiVersion + cpiChecksum)
        }
    }
}
