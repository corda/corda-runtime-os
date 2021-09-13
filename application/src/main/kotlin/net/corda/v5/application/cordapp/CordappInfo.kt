package net.corda.v5.application.cordapp

import net.corda.v5.crypto.SecureHash

/**
 * A [CordappInfo] describes a single CorDapp currently installed on the node
 *
 * @property type A description of what sort of CorDapp this is - either a contract, workflow, or a combination.
 * @property name The name of the JAR file that defines the CorDapp
 * @property shortName The name of the CorDapp
 * @property minimumPlatformVersion The minimum platform version the node must be at for the CorDapp to run
 * @property targetPlatformVersion The target platform version this CorDapp has been tested against
 * @property version The version of this CorDapp
 * @property vendor The vendor of this CorDapp
 * @property licence The name of the licence this CorDapp is released under
 * @property jarHash The hash of the JAR file that defines this CorDapp
 */
interface CordappInfo {
    val type: String
    val name: String
    val shortName: String
    val minimumPlatformVersion: Int
    val targetPlatformVersion: Int
    val version: String
    val vendor: String
    val licence: String
    val jarHash: SecureHash
}