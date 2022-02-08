@file:JvmName("Constants")
package net.corda.example.vnode

import java.util.Collections.unmodifiableList
import net.corda.packaging.CPK
import net.corda.v5.crypto.SecureHash

const val EXAMPLE_CPI_RESOURCE = "META-INF/example-cpi-package.cpb"
const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

const val BASE_DIRECTORY_KEY = "baseDirectory"

// The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
@JvmField
val PLATFORM_PUBLIC_BUNDLE_NAMES: List<String> = unmodifiableList(listOf(
    "javax.persistence-api",
    "jcl.over.slf4j",
    "net.corda.application",
    "net.corda.base",
    "net.corda.cipher-suite",
    "net.corda.crypto",
    "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
    "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
    "net.corda.persistence",
    "net.corda.serialization",
    "org.apache.aries.spifly.dynamic.bundle",
    "org.apache.felix.framework",
    "org.apache.felix.scr",
    "org.hibernate.orm.core",
    "org.jetbrains.kotlin.osgi-bundle",
    "slf4j.api"
))

val CPK.id: CPK.Identifier get() = metadata.id
val CPK.hash: SecureHash get() = metadata.hash
