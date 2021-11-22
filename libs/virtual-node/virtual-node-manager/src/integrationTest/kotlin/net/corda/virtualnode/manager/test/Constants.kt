package net.corda.virtualnode.manager.test

object Constants {
    // The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
    val PLATFORM_PUBLIC_BUNDLE_NAMES = listOf(
        "javax.persistence-api",
        "jcl.over.slf4j",
        "net.corda.application",
        "net.corda.base",
        "net.corda.cipher-suite",
        "net.corda.crypto",
        "net.corda.crypto-impl",
        "net.corda.flows",
        "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
        "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
        "net.corda.ledger",
        "net.corda.persistence",
        "net.corda.serialization",
        "org.apache.aries.spifly.dynamic.bundle",
        "org.apache.felix.framework",
        "org.apache.felix.scr",
        "org.hibernate.orm.core",
        "org.jetbrains.kotlin.osgi-bundle",
        "slf4j.api"
    )

    internal const val DIGEST_CPB_ONE = "crypto-custom-digest-one-consumer-cpk-package.cpb"
    internal const val DIGEST_CPB_TWO = "crypto-custom-digest-two-consumer-cpk-package.cpb"

    internal const val BASE_DIRECTORY_KEY = "baseDirectory"
    internal const val BLACKLISTED_KEYS_KEY = "blacklistedKeys"
    internal const val PLATFORM_VERSION_KEY = "platformVersion"
}
