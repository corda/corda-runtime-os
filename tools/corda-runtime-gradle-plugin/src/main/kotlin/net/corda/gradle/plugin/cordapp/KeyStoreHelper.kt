package net.corda.gradle.plugin.cordapp

class KeyStoreHelper {

    private val aliasFlag = "-alias"
    private val keystoreFlag = "-keystore"
    private val storepassFlag = "-storepass"

    fun generateKeyPair(
        javaBinDir: String,
        keystoreAlias: String,
        keystorePassword: String,
        keystoreFilePath: String
    ){
        val cmdList = listOf(
            "$javaBinDir/keytool",
            "-genkeypair",
            aliasFlag,
            keystoreAlias,
            keystoreFlag,
            keystoreFilePath,
            storepassFlag,
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
        aliasValue: String,
        fileName: String
    ) {
        val cmdList = listOf(
            "$javaBinDir/keytool",
            "-importcert",
            keystoreFlag,
            keystoreFilePath,
            storepassFlag,
            keystorePassword,
            "-noprompt",
            aliasFlag,
            aliasValue,
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
            "$javaBinDir/keytool",
            "-exportcert",
            "-rfc",
            aliasFlag,
            keystoreAlias,
            keystoreFlag,
            keystoreFilePath,
            storepassFlag,
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