package net.corda.cli.plugins.packaging

import picocli.CommandLine

@CommandLine.Command(
    name = "list-keys",
    description = ["Tests AWS KMS connection and prints all KMS keys."]
)
class ListAwsKmsKeys : Runnable {

    override fun run() {
        val kmsClient = getKmsClient()
        listAllKeys(kmsClient)
        closeKmsClient(kmsClient)
    }
}
