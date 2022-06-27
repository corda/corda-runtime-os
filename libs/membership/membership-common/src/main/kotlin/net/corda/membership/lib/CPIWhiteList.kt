package net.corda.membership.lib

interface CPIWhiteList {
    val cpiVersions: List<CpiVersion>
}
