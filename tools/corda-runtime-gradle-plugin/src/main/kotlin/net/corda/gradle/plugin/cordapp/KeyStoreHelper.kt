package net.corda.gradle.plugin.cordapp

class KeyStoreHelper {

    fun generateKeyPair(
        javaBinDir: String,
        keystoreAlias: String,
        keystorePassword: String,
        keystoreFilePath: String
    ){
        val cmdList = listOf(
            "$javaBinDir/keytool",
            "-genkeypair",
            "-alias",
            keystoreAlias,
            "-keystore",
            keystoreFilePath,
            "-storepass",
            keystorePassword,
            "-dname",
            "CN=CPI Example - My Signing Key, O=CorpOrgCorp, L=London, C=GB",
            "-keyalg",
            "RSA",
            "-storetype",
            "pkcs12",
            "-validity",
            "4000"
        )
        runCommand(cmdList)
    }

    fun importKeystoreCert(
        javaBinDir: String,
        keystorePassword: String,
        keystoreFilePath: String,
        alias: String,
        fileName: String
    ) {
        val cmdList = listOf(
            "$javaBinDir/keytool",
            "-importcert",
            "-keystore",
            keystoreFilePath,
            "-storepass",
            keystorePassword,
            "-noprompt",
            "-alias",
            alias,
            "-file",
            fileName
        )
        runCommand(cmdList)
    }

    fun exportCert(
        javaBinDir: String,
        keystoreAlias: String,
        keystoreFilePath: String,
        keystoreCertFilePath: String
    ) {

        val cmdList = listOf(
            javaBinDir + "/keytool",
            "-exportcert",
            "-rfc",
            "-alias",
            keystoreAlias,
            "-keystore",
            keystoreFilePath,
            "-storepass",
            "keystore password",
            "-file",
            keystoreCertFilePath
        )

        runCommand(cmdList)
    }

    private fun runCommand(cmd: List<String>) {
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.inputStream.transferTo(System.out)
        proc.waitFor()
    }
}