package net.corda.libs.statemanager

data class State<S>(
    val state: S,
    val key: String,
    val version: String,
    val modifiedTime: Long,
    val metadata: PrimitiveTypeMap<String, Any>,
)