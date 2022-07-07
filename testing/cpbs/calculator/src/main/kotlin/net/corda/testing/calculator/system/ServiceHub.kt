package net.corda.testing.calculator.system

class ServiceHub {

    data class Nmap(val identities: List<String>)
    fun nmap() = Nmap(emptyList())


    data class VaultService(
        val transactions: Map<String, ByteArray>
    )

    fun vaultService() = VaultService(emptyMap())
}