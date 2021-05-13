package net.corda.tools.kafka.util

import java.io.FileInputStream
import java.util.*

fun loadConfig(configFile: String) = FileInputStream(configFile).use {
  Properties().apply {
    load(it)
  }
}