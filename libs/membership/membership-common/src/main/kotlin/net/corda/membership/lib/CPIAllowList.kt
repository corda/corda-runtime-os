package net.corda.membership.lib

interface CPIAllowList {
    val cpiVersions: List<CpiVersion>
}
