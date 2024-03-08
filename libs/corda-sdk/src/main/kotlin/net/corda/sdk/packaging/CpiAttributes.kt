package net.corda.sdk.packaging

/**
 * CPI manifest attributes
 * @property cpiName: String - CPI name
 * @property cpiVersion: String - CPI version
 * @property cpiUpgrade: Boolean - Allow upgrade without flow draining
 */
data class CpiAttributes(
    val cpiName: String,
    val cpiVersion: String,
    val cpiUpgrade: Boolean,
)
