package net.corda.session.manager

class Constants {
    companion object {
        val INITIATED_SESSION_ID_SUFFIX = "-INITIATED"
        val FLOW_SESSION_SUBSYSTEM = "flowSession"

        //SESSION CONTEXT PROPERTIES
        const val FLOW_PROTOCOL = "corda.protocol"
        const val FLOW_PROTOCOL_VERSIONS_SUPPORTED = "corda.protocol.versions.supported"
        const val FLOW_PROTOCOL_VERSION_USED = "corda.protocol.version"
    }
}