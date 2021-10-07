package net.corda.cpi.read.impl.file

import java.nio.file.Path

interface CPIFileListener {

    fun newCPI(cpiPath: Path)

    fun modifiedCPI(cpiPath: Path)

    fun deletedCPI(cpiPath: Path)
}