package net.corda.sandboxtests

// The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
@JvmField
val PLATFORM_PUBLIC_BUNDLE_NAMES = listOf(
    "javax.persistence-api",
    "jcl.over.slf4j",
    "net.corda.application",
    "net.corda.base",
    "net.corda.crypto",
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

internal const val BASE_DIRECTORY_KEY = "baseDirectory"
internal const val BLACKLISTED_KEYS_KEY = "blacklistedKeys"
internal const val PLATFORM_VERSION_KEY = "platformVersion"

internal const val CPK_ONE = "sandbox-cpk-one-cordapp.cpk"
internal const val CPK_TWO = "sandbox-cpk-two-cordapp.cpk"
internal const val CPK_THREE = "sandbox-cpk-three-cordapp.cpk"
internal const val CPK_1_JODATIME_VERSION = "2.10.10"
internal const val CPK_2_JODATIME_VERSION = "2.10.9"

internal const val FRAMEWORK_BUNDLE_NAME = "org.apache.felix.framework"
internal const val SCR_BUNDLE_NAME = "org.apache.felix.scr"
internal const val CPK_ONE_BUNDLE_NAME = "com.example.sandbox.sandbox-cpk-one"
internal const val CPK_TWO_BUNDLE_NAME = "com.example.sandbox.sandbox-cpk-two"
internal const val CPK_LIBRARY_BUNDLE_NAME = "com.example.sandbox.sandbox-cpk-library"

internal const val LIBRARY_VERSION_FLOW_CPK_1 = "com.example.sandbox.cpk1.LibraryVersionFlow"
internal const val LIBRARY_VERSION_FLOW_CPK_2 = "com.example.sandbox.cpk2.LibraryVersionFlow"
internal const val BUNDLE_EVENTS_FLOW = "com.example.sandbox.cpk1.BundleEventOneFlow"
internal const val BUNDLES_FLOW = "com.example.sandbox.cpk1.BundlesOneFlow"
internal const val INVOKE_PRIVATE_IMPL_FLOW = "com.example.sandbox.cpk1.InvokePrivateImplFlow"
internal const val PRIVATE_IMPL_AS_GENERIC_FLOW = "com.example.sandbox.cpk1.PrivateImplAsGenericFlow"
internal const val SERVICE_EVENTS_FLOW = "com.example.sandbox.cpk1.ServiceEventOneFlow"
internal const val SERVICES_FLOW_CPK_1 = "com.example.sandbox.cpk1.ServicesOneFlow"
internal const val SERVICES_FLOW_CPK_2 = "com.example.sandbox.cpk2.ServicesTwoFlow"
internal const val SERVICES_FLOW_CPK_3 = "com.example.sandbox.cpk3.ServicesThreeFlow"
internal const val GET_CALLING_SANDBOX_GROUP_FUNCTION = "com.example.sandbox.cpk1.GetCallingSandboxGroupFunction"
internal const val LIB_SINGLETON_SERVICE_FLOW_CPK_1 = "com.example.sandbox.cpk1.LibrarySingletonServiceHolderFlow"
internal const val LIB_SINGLETON_SERVICE_FLOW_CPK_2 = "com.example.sandbox.cpk2.LibrarySingletonServiceHolderFlow"

internal const val LIBRARY_QUERY_CLASS = "com.example.sandbox.library.SandboxQuery"
internal const val LIBRARY_QUERY_IMPL_CLASS = "com.example.sandbox.library.impl.SandboxQueryImpl"
internal const val SYSTEM_BUNDLE_CLASS = "org.apache.felix.framework.BundleImpl"

internal const val PRIVATE_WRAPPER_RETURN_VALUE = "String returned by WrapperImpl."

internal const val SHA256 = "SHA-256"