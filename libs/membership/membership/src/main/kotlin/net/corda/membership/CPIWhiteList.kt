package net.corda.membership

interface CPIWhiteList {
    val cpiVersions: List<CpiVersion>
}
