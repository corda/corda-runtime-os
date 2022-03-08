package net.corda.libs.cpiupload.endpoints.v1

data class CpkMetadata(
    val id : CpkIdentifier,
    // TODO do we need manifest?
    // val manifest : CPK.Manifest,
    val mainBundle : String,
    val libraries : List<String>,
    val dependencies : List<CpkIdentifier>,
    // TODO do we need cordappManifest?
    // val cordappManifest : CordappManifest,
    val type : String,
    val hash: String)