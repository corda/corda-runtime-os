package net.corda.crypto.test.certificates.generation

import com.fasterxml.jackson.databind.ObjectMapper
import org.bouncycastle.util.io.pem.PemReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class TinyCertCertificatesAuthority(
    private var apiKey: String,
    private var passPhrase: String,
    private var email: String,
    private val authorityName: String,
) : CloseableCertificateAuthority {
    private val digester =
        Mac.getInstance("HmacSHA256").also { mac ->
            mac.init(
                SecretKeySpec(
                    apiKey.toByteArray(),
                    "HmacSHA256"
                )
            )
        }

    private val client = HttpClient.newHttpClient()
    private val mapper = ObjectMapper()
    private val certificateFactory = CertificateFactory.getInstance("X.509")
    private val keyFactory = KeyFactory.getInstance("RSA")

    private fun callApi(api: String, data: Map<String, String>): String {
        val encodedData = data.toSortedMap().mapValues {
            URLEncoder.encode(it.value, "utf-8")
        }.mapKeys {
            URLEncoder.encode(it.key, "utf-8")
        }.map { (key, value) ->
            "$key=$value"
        }.joinToString("&")

        val rawDigest = digester.doFinal(encodedData.toByteArray())
        val digest = rawDigest.joinToString(separator = "") {
                eachByte ->
            "%02x".format(eachByte)
        }
        val postData = "$encodedData&digest=$digest"
        val connect = HttpRequest.newBuilder()
            .uri(URI("https://www.tinycert.org/api/v1/$api"))
            .header("content-type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(postData))
            .build()
        val response = client.send(
            connect,
            HttpResponse.BodyHandlers.ofString()
        )
        return response.body()
    }

    @Suppress("UNCHECKED_CAST")
    private fun callApiMap(api: String, data: Map<String, String>): Map<String, Any?> {
        return mapper.readValue(
            callApi(api, data),
            Map::class.java
        ) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun callApiList(api: String, data: Map<String, String>): Collection<Map<String, Any?>> {
        return mapper.readValue(
            callApi(api, data),
            Collection::class.java
        ) as Collection<Map<String, Any?>>
    }

    private val token by lazy {
        val called = callApiMap(
            "connect",
            mapOf(
                "email" to email,
                "passphrase" to passPhrase
            )
        )
        called["token"] as String
    }

    private fun disconnect() {
        callApi(
            "disconnect",
            mapOf(
                "token" to token,
            )
        )
    }

    private fun certificationAuthorities() =
        callApiList(
            "ca/list",
            mapOf(
                "token" to token,
            )
        )

    private val authority by lazy {
        val knownAuthority = certificationAuthorities().firstOrNull {
            it["name"] == authorityName
        }
        if (knownAuthority == null) {
            callApiMap(
                "ca/new",
                mapOf(
                    "C" to "GB",
                    "O" to authorityName,
                    "hash_method" to "sha256",
                    "token" to token
                )
            )["ca_id"] as Number
        } else {
            knownAuthority["id"] as Number
        }
    }

    private fun certificates() =
        callApiList(
            "cert/list",
            mapOf(
                "ca_id" to authority.toString(),
                "what" to "2",
                "token" to token,
            )
        )

    override val caCertificate by lazy {
        val pem = callApiMap(
            "ca/get",
            mapOf(
                "ca_id" to authority.toString(),
                "token" to token,
                "what" to "cert"
            )
        )["pem"] as String
        pem.byteInputStream().use {
            certificateFactory.generateCertificate(it)
        }
    }

    private fun getCertificateForHosts(hosts: Collection<String>): Number {
        val knownCertificate = certificates().firstOrNull {
            it["name"] == hosts.first()
        }
        return if (knownCertificate == null) {
            val sans = hosts.mapIndexed { index, host ->
                "SANs[$index][DNS]" to host
            }.toMap()
            (
                callApiMap(
                    "cert/new",
                    mapOf(
                        "C" to "GB",
                        "CN" to hosts.first(),
                        "O" to authorityName,
                        "ca_id" to authority.toString(),
                        "token" to token
                    ) + sans
                )["cert_id"] as Number
                )
        } else {
            knownCertificate["id"] as Number
        }
    }

    override fun generateKeyAndCertificate(hosts: Collection<String>): PrivateKeyWithCertificate {
        val certificateId = getCertificateForHosts(hosts)
        val certificatePem = callApiMap(
            "cert/get",
            mapOf(
                "cert_id" to certificateId.toString(),
                "token" to token,
                "what" to "chain"
            )
        )["pem"] as String

        val privateKeyPem = callApiMap(
            "cert/get",
            mapOf(
                "cert_id" to certificateId.toString(),
                "token" to token,
                "what" to "key.dec"
            )
        )["pem"] as String
        val certificate = certificatePem.byteInputStream().use {
            certificateFactory.generateCertificate(it)
        }

        val key = privateKeyPem.reader().use {
            PemReader(it).use {
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(it.readPemObject().content))
            }
        }

        return PrivateKeyWithCertificate(key, certificate)
    }

    override fun close() {
        disconnect()
    }
}
