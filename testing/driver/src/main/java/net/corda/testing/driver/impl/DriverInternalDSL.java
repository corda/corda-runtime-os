package net.corda.testing.driver.impl;

import net.corda.testing.driver.DriverDSL;
import net.corda.testing.driver.Framework;
import org.jetbrains.annotations.NotNull;

/**
 * This internal interface is package private,
 * to keep it hidden from Java code.
 */
interface DriverInternalDSL extends DriverDSL {
    @NotNull
    Framework getFramework();
}
