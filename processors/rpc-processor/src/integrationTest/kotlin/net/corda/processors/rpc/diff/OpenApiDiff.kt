package net.corda.processors.rpc.diff

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info

internal fun OpenAPI.diff(baseline: OpenAPI): List<String> {
    return info.diff(baseline.info) +
            paths.diff(baseline.paths)
    // todo: tags, components
}

private fun Info.diff(baseline: Info): List<String> {
    return if (this != baseline) {
        listOf("Info is different, baseline: $baseline, current: $this")
    } else emptyList()
}

private fun Paths.diff(baseline: Paths): List<String> {
    val differences = mutableListOf<String>()
    if (size != baseline.size) {
        differences.add("Different number of paths, baseline: ${baseline.size}, current: $size")
        val currentSet = this.keys
        val baselineSet = baseline.keys

        baselineSet.subtract(currentSet).forEach {
            differences.add("In baseline but not in current: $it")
        }

        currentSet.subtract(baselineSet).forEach {
            differences.add("In current but not in baseline: $it")
        }
    }

    entries.forEach { entry ->
        val baselineValue: PathItem? = baseline[entry.key]
        if(baselineValue == null) {
            differences.add("Absent in baseline: Current path: ${entry.key}")
        } else {
            if (entry.value != baselineValue) {
                differences.add("Different path at [${entry.key}]. Current path: ${entry.value} is different to baseline: $baselineValue")
            }
        }
    }

    return differences
}
