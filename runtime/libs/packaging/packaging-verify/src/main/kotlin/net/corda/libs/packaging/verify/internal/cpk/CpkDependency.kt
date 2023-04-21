package net.corda.libs.packaging.verify.internal.cpk

internal interface CpkDependency {
    val name: String
    val version: String
    fun satisfied(cpks: List<AvailableCpk>): Boolean
}