package net.corda.sandbox.service.tests

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

    internal const val CPB_ONE = "sandbox-service-test-cpb-one-package.cpb"
    internal const val CPB_TWO = "sandbox-service-test-cpb-two-package.cpb"
    internal const val CPB_THREE = "sandbox-service-test-cpb-three-package.cpb"

    internal const val BASE_DIRECTORY_KEY = "baseDirectory"
    internal const val BLACKLISTED_KEYS_KEY = "blacklistedKeys"
    internal const val PLATFORM_VERSION_KEY = "platformVersion"

    internal const val CPB_DIRECTORY = "cpb.directory"
    internal const val BASE_DIRECTORY = "base.directory"
}
