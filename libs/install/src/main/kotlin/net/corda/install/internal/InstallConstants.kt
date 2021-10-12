@file:JvmName("InstallConstants")

package net.corda.install.internal

import net.corda.packaging.CPK

// The supported CPK formats.
internal val SUPPORTED_CPK_FORMATS = sortedSetOf(CPK.FormatVersion.parse("1.0"))

// Configuration admin property keys.
internal const val CONFIG_ADMIN_BASE_DIRECTORY = "baseDirectory"
internal const val CONFIG_ADMIN_PLATFORM_VERSION = "platformVersion"
internal const val CONFIG_ADMIN_BLACKLISTED_KEYS = "blacklistedKeys"

// Relative directory where CPK files are stored on disk.
internal const val CPK_DIRECTORY = "cpks"

// Relative directory where CPK/CPB files are extracted on disk.
internal const val EXTRACTION_DIRECTORY = "extracted"

// Constants related to the structure of CPK files.
internal const val JAR_EXTENSION = "jar"
