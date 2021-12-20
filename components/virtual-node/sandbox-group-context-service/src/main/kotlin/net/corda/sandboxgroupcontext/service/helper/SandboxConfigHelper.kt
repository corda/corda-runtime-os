package net.corda.sandboxgroupcontext.service.helper

import net.corda.sandbox.SandboxCreationService
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import java.util.Collections.unmodifiableList
import java.util.Hashtable

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
    "net.corda.ledger",
    "net.corda.persistence",
    "net.corda.serialization",
    "org.apache.aries.spifly.dynamic.bundle",
    "org.apache.felix.framework",
    "org.apache.felix.scr",
    "org.hibernate.orm.core",
    "org.jetbrains.kotlin.osgi-bundle",
    "slf4j.api"
))

internal const val BASE_DIRECTORY_KEY = "baseDirectory"
internal const val BLACKLISTED_KEYS_KEY = "blacklistedKeys"
internal const val PLATFORM_VERSION_KEY = "platformVersion"

/**
 * Configure the ConfigurationAdmin with the details with the basic information required to load the install module.
 * Assign public bundles to the public sandbox.
 */
fun initPublicSandboxes(
    configurationAdmin: ConfigurationAdmin,
    sandboxCreationService: SandboxCreationService,
    baseDirectory: String
) {
    configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
        val properties = Hashtable<String, Any?>()
        properties[BASE_DIRECTORY_KEY] = baseDirectory
        properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
        properties[PLATFORM_VERSION_KEY] = 999
        config.update(properties)
    }

    val allBundles = FrameworkUtil.getBundle(SandboxGroupContextComponent::class.java).bundleContext.bundles
    val (publicBundles, privateBundles) = allBundles.partition { bundle ->
        bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
    }
    sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
}