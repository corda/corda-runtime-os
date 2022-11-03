package net.corda.libs.cpi.datamodel

interface CpkDbChangelog {
    val filePath: String
    val content: String
}
