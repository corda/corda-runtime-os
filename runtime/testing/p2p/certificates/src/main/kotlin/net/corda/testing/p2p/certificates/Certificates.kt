package net.corda.testing.p2p.certificates

class Certificates {
    companion object {
        val truststoreCertificatePem = this::class.java.classLoader.getResource("truststore/certificate.pem")!!
        val c4TruststoreCertificatePem = this::class.java.classLoader.getResource("truststore_c4/cordarootca.pem")!!
        val aliceKeyStoreFile = this::class.java.classLoader.getResource("sslkeystore_alice.jks")!!
        val bobKeyStoreFile = this::class.java.classLoader.getResource("sslkeystore_bob.jks")!!
        val chipKeyStoreFile = this::class.java.classLoader.getResource("sslkeystore_chip.jks")!!
        val daleKeyStoreFile = this::class.java.classLoader.getResource("sslkeystore_dale.jks")!!
        val c4KeyStoreFile = this::class.java.classLoader.getResource("sslkeystore_c4.jks")!!
        val c5KeyStoreFile = this::class.java.classLoader.getResource("sslkeystore_c5.jks")!!
        val ecTrustStorePem = this::class.java.classLoader.getResource("ec_truststore.pem")!!
        val receiverKeyStoreFile = this::class.java.classLoader.getResource("receiver.jks")!!
        val senderKeyStoreFile = this::class.java.classLoader.getResource("sender.jks")!!
        val aliceKeyStorePem = this::class.java.classLoader.getResource("certificate_alice.pem")!!
        val bobKeyStorePem = this::class.java.classLoader.getResource("certificate_bob.pem")!!
        val ipKeyStore = this::class.java.classLoader.getResource("sslkeystore_127.0.0.1.jks")!!
    }
}