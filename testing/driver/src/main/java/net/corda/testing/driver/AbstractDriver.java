package net.corda.testing.driver;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.corda.data.KeyValuePair;
import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.testing.driver.function.ThrowingFunction;
import net.corda.testing.driver.impl.DriverDSLImpl;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

abstract class AbstractDriver {
    private final Map<MemberX500Name, KeyPair> members;
    private final Map<MemberX500Name, KeyPair> notaries;
    private final Set<KeyValuePair> groupParameters;
    private ClassLoader originalTCCL;
    private DriverDSLImpl driver;

    AbstractDriver(
        @NotNull Map<MemberX500Name, KeyPair> members,
        @NotNull Map<MemberX500Name, KeyPair> notaries,
        @NotNull Set<KeyValuePair> groupParameters
    ) {
        requireNonNull(members);
        requireNonNull(notaries);
        requireNonNull(groupParameters);

        this.members = members;
        this.notaries = notaries;
        this.groupParameters = groupParameters;
    }

    private void checkDriver() {
        assertNotNull(driver, getClass().getSimpleName() + " not registered as a JUnit extension.");
    }

    private void publishReport(@NotNull ExtensionContext context, @NotNull String methodName) {
        final Map<String, String> map = new HashMap<>();
        map.put("driverTestClass", context.getRequiredTestClass().getName());
        map.put("driverMethod", methodName);
        map.put("driverType", getClass().getSimpleName());
        context.publishReportEntry(map);
    }

    protected final void createDriver(@NotNull ExtensionContext context) {
        publishReport(context, "createDriver");
        originalTCCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            driver = new DriverDSLImpl(members, notaries, groupParameters);
        } catch (RuntimeException | Error e) {
            Thread.currentThread().setContextClassLoader(originalTCCL);
            throw e;
        }
    }

    protected final void destroyDriver(@NotNull ExtensionContext context) throws Exception {
        publishReport(context, "destroyDriver");
        try {
            if (driver != null) {
                driver.close();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalTCCL);
        }
    }

    public final <R> R let(@NotNull ThrowingFunction<DriverDSL, R> action) {
        checkDriver();
        return action.apply(driver);
    }

    public final void run(@NotNull ThrowingConsumer<DriverDSL> action) {
        checkDriver();
        action.accept(driver);
    }
}
