@file:JvmName("SandboxConstants")

package net.corda.sandbox.internal

import java.util.Collections.unmodifiableList

// The symbolic names of the bundles that should be public in the platform sandbox.
// Wrapped inside an unmodifiable list to forbid any tampering.
internal val PUBLIC_PLATFORM_BUNDLE_NAMES: List<String> = unmodifiableList(
    listOf(
        "javax.persistence-api",
        "jcl.over.slf4j",
        "net.corda.application",
        "net.corda.base",
        "net.corda.crypto-api",
        "net.corda.flows",
        "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
        "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
        "net.corda.ledger",
        "net.corda.legacy-api",
        "net.corda.persistence",
        "net.corda.serialization",
        "org.apache.aries.spifly.dynamic.bundle",
        "org.apache.felix.framework",
        "org.apache.felix.scr",
        "org.hibernate.orm.core",
        "org.jetbrains.kotlin.osgi-bundle",
        "slf4j.api"
    )
)
