package net.corda.cpiinfo.read

import net.corda.cpiinfo.CpiInfoReader
import net.corda.lifecycle.Lifecycle

interface CpiInfoReaderComponent : CpiInfoReader, Lifecycle
