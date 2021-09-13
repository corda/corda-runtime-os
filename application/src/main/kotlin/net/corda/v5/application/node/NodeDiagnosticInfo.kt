package net.corda.v5.application.node

import net.corda.v5.application.cordapp.CordappInfo

/**
 * A [NodeDiagnosticInfo] holds information about the current node version.
 * @property version The current node version string, e.g. 4.3, 4.4-SNAPSHOT. Note that this string is effectively freeform, and so should only
 *                be used for providing diagnostic information. It should not be used to make functionality decisions (the platformVersion
 *                is a better fit for this).
 * @property revision The git commit hash this node was built from
 * @property platformVersion The platform version of this node. This number represents a released API version, and should be used to make
 *                        functionality decisions (e.g. enabling an app feature only if an underlying platform feature exists)
 * @property vendor The vendor of this node
 * @property cordapps A list of CorDapps currently installed on this node
 */
interface NodeDiagnosticInfo {
    val version: String
    val revision: String
    val platformVersion: Int
    val vendor: String
    val cordapps: List<CordappInfo>
}