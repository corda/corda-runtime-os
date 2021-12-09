package net.corda.cpiinfo.write

import net.corda.cpiinfo.CpiInfoWriter
import net.corda.lifecycle.Lifecycle

interface CpiInfoWriterComponent : CpiInfoWriter, Lifecycle
