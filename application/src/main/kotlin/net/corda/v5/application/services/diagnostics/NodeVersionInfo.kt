package net.corda.v5.application.services.diagnostics

/**
 * Version info about the node. Note that this data should be used for diagnostics purposes only - it is unsafe to rely on this for
 * functional decisions.
 *
 * @property releaseVersion The release version string of the node, e.g. 4.3, 4.4-SNAPSHOT.
 * @property revision The git commit hash this node was built from
 * @property platformVersion The platform version of this node, representing the released API version.
 * @property vendor The vendor of this node
 */
interface NodeVersionInfo {
    val releaseVersion: String
    val revision: String
    val platformVersion: Int
    val vendor: String
}