package net.corda.packaging.internal

internal object PackagingConstants {
    internal const val CPI_NAME_ATTRIBUTE = "Corda-CPB-Name"
    internal const val CPI_VERSION_ATTRIBUTE = "Corda-CPB-Version"

    internal const val JAR_FILE_EXTENSION = ".jar"
    internal const val CPK_LIB_FOLDER = "lib" // The folder that contains a CPK's library JARs.
    private const val META_INF_FOLDER = "META-INF"

    // The filename of the file specifying a CPK's dependencies.
    internal const val CPK_DEPENDENCIES_FILE_NAME = "CPKDependencies"
    internal const val CPK_DEPENDENCIES_FILE_ENTRY = "$META_INF_FOLDER/$CPK_DEPENDENCIES_FILE_NAME"
}

