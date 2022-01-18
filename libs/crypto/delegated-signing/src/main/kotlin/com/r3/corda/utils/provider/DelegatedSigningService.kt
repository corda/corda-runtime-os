package com.r3.corda.utils.provider

import java.security.cert.X509Certificate

interface DelegatedSigningService {

    fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray?

    fun certificates(): Map<String, List<X509Certificate>>

    fun aliases(): Set<String> = certificates().keys

    fun certificates(alias: String): List<X509Certificate>? = certificates()[alias]

    fun certificate(alias: String): X509Certificate? = certificates(alias)?.first()
}