package net.corda.db.admin.impl

import net.corda.db.admin.DbChange
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.v5.base.util.contextLogger
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLEncoder
import java.util.Collections.unmodifiableSet


fun normalizePath(path: String) = path.replace("//+".toRegex(), "/")

/**
 * Classloader implementation of [DbChange]
 * This allows ChangeLog files to be fetched that are present in the classloader as resource files.
 *
 * Keeps track and exposes the files that have been fetched, which is used by StreamResourceAccessor to
 * generate a "master" changelog file.
 *
 * As far as Liquibase changelog files are concerned, the absolute path to this changelog will be like:
 *
 *   classloader://foo/migration/test/fred.txt
 *
 *  Note: if liquibase tries to read the master changelog before fetching all the constituent changelog files then
 *  any which have not been fetched will not come through.
 *
 * @property changelogFiles of [ChangeLogResourceFiles] that will be processed in order. Associated classloaders will be de-duped.
 * @constructor Create initially empty Classloader change log
 */
class ClassloaderChangeLog(
    private val changelogFiles: LinkedHashSet<ChangeLogResourceFiles>,
) : DbChange {
    /**
     * Definition of a master change log file and associated classloader
     * [name] is required so to be able to support "overlapping" resource files.
     * I.e. if 2 resource files with the same name exist in 2 different classloaders (e.g. migration/bob.xml in 2
     * different bundles), then a name must be specified and this name can be uses in the "file" attribute of an
     * "include" tag in a ChangeLog file.
     * Name could be the jar or bundle name, for example.
     *
     * @property name that uniquely identifies the set of changelogs related to the classloader
     * @property masterFiles one or more master ChangeLog files in order the should be executed.
     * @property classLoader defaulted to current classloader
     */
    data class ChangeLogResourceFiles(
        val name: String,
        val masterFiles: List<String>,
        val classLoader: ClassLoader = ChangeLogResourceFiles::class.java.classLoader
    ) {
        init {
            if (masterFiles.isEmpty()) {
                throw IllegalArgumentException("masterFiles must have at least one item.")
            }
        }
    }

    companion object {
        const val CLASS_LOADER_PREFIX = "classloader://"

        private val log = contextLogger()
    }

    private val allChangeFiles = mutableSetOf<String>()

    private val distinctLoaders by lazy {
        changelogFiles.mapTo(HashSet(), ChangeLogResourceFiles::classLoader)
    }

    override val masterChangeLogFiles by lazy {
        changelogFiles.flatMap { clf ->
            clf.masterFiles.map { mf ->
                "$CLASS_LOADER_PREFIX${URLEncoder.encode(clf.name, "utf-8")}/${mf}"
            }
        }.distinct()
    }

    override val changeLogFileList: Set<String>
        get() {
            return unmodifiableSet(allChangeFiles)
        }

    private fun fetchFromNominatedClassloader(path: String): InputStream? {
        val splitPath = normalizePath(path).removePrefix(normalizePath(CLASS_LOADER_PREFIX)).split('/', limit = 2)
        if (splitPath.size != 2 || splitPath[1].isEmpty())
            throw IllegalArgumentException("$path is not a valid classloader resource path.")
        val cl = changelogFiles.firstOrNull { URLEncoder.encode(it.name, "utf-8") == splitPath[0] }
            ?: throw IllegalArgumentException("Cannot find classloader ${splitPath[0]} from $path")
        allChangeFiles.add(path)
        return cl.classLoader.getResourceAsStream(splitPath[1])
    }

    // Search across all class loaders for path.
    // If leaf is set then strip out all directory elements from path and just look for the plain filename
    // in each classloader, which is our last ditch attempt to find something and is only used when
    // searching for the full path name fails.
    private fun fetchAllClassLoaders(path: String, leaf: Boolean = false): InputStream? =
        distinctLoaders.firstNotNullOfOrNull {
            val usePath = if (leaf) path.split('/').last() else path
            val r =  it.getResourceAsStream(usePath)
            val resultWord = if (r != null) "FOUND" else "MISSING"
            log.info("Classloader changelog resolution $resultWord for $path on $it")
            r?.apply {
                allChangeFiles.add(usePath)
            }
        }

    // Lookup a path.
    // If path begins with CLASS_LOADER_PREFIX look only in a nominated classloader otherwise
    // search every class loader we know. Allow arbitrary length sequences of slashes when checking for
    // CLASS_LOADER_PREFIX, in part to cope with liquibase's normalization.
    override fun fetch(path: String): InputStream  {
        val stream = (if (normalizePath(path).startsWith(normalizePath(CLASS_LOADER_PREFIX))) {
            fetchFromNominatedClassloader(path)
        } else {
            fetchAllClassLoaders(path) ?: fetchAllClassLoaders(path, leaf = true)
        })
        log.trace("resolved {} to {} across {}", path, stream, distinctLoaders)
        return stream ?: throw FileNotFoundException("Changelog $path not found across ${distinctLoaders.size} class loaders")
    }
}
