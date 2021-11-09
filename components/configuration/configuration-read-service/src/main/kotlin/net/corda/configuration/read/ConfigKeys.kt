package net.corda.configuration.read

class ConfigKeys {
    companion object {
        const val MESSAGING_KEY = "corda.messaging"
        const val FLOW_KEY = "corda.flow"
        //tmp solution until new boot config logic added
        const val BOOTSTRAP_KEY = "corda.boot"
    }
}