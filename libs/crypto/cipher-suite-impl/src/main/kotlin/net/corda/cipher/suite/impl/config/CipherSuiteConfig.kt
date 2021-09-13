package net.corda.cipher.suite.impl.config

import com.typesafe.config.Config

class CipherSuiteConfig(private val raw: Config) {
    companion object {
        const val DEFAULT_VALUE = "default"
    }

    val schemeMetadataProvider: String
        get() = if (raw.hasPath(this::schemeMetadataProvider.name)) {
            raw.getString(this::schemeMetadataProvider.name)
        } else {
            DEFAULT_VALUE
        }

    val signatureVerificationProvider: String
        get() = if (raw.hasPath(this::signatureVerificationProvider.name)) {
            raw.getString(this::signatureVerificationProvider.name)
        } else {
            DEFAULT_VALUE
        }

    val digestProvider: String
        get() = if (raw.hasPath(this::digestProvider.name)) {
            raw.getString(this::digestProvider.name)
        } else {
            DEFAULT_VALUE
        }
}