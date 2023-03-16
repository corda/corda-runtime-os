/**
 * This package is only exported because an otherwise internal interface is being accessed from an example application outside this module.
 * An `impl` package has been added underneath this package to keep OSGI happy.
 */
@QuasarIgnoreSubPackages
@Export
package net.corda.interop;

import co.paralleluniverse.quasar.annotations.QuasarIgnoreSubPackages;
import org.osgi.annotation.bundle.Export;