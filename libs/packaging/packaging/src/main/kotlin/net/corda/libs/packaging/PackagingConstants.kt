package net.corda.libs.packaging

object PackagingConstants {
    const val CPI_NAME_ATTRIBUTE = "Corda-CPB-Name"
    const val CPI_VERSION_ATTRIBUTE = "Corda-CPB-Version"
    const val CPI_GROUP_POLICY_ENTRY = "GroupPolicy.json"

    const val JAR_FILE_EXTENSION = ".jar"
    const val CPK_LIB_FOLDER = "lib" // The folder that contains a CPK's library JARs.
    private const val META_INF_FOLDER = "META-INF"

    // The filename of the file specifying a CPK's dependencies.
    const val CPK_DEPENDENCIES_FILE_NAME = "CPKDependencies"
    const val CPK_DEPENDENCY_CONSTRAINTS_FILE_NAME = "DependencyConstraints"
    const val CPK_DEPENDENCIES_FILE_ENTRY = "$META_INF_FOLDER/$CPK_DEPENDENCIES_FILE_NAME"
    const val CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY = "$META_INF_FOLDER/$CPK_DEPENDENCY_CONSTRAINTS_FILE_NAME"
}
