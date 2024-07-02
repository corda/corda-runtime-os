package net.corda.membership.certificates

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter

fun String.toPemCertificateChain(): List<String> = reader().use { reader ->
    PEMParser(reader).use {
        generateSequence { it.readObject() }
            .filterIsInstance<X509CertificateHolder>()
            .map { certificate ->
                StringWriter().use { str ->
                    JcaPEMWriter(str).use { writer ->
                        writer.writeObject(certificate)
                    }
                    str.toString()
                }
            }
            .toList()
    }
}
