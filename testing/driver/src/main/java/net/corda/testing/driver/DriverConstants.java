package net.corda.testing.driver;

public final class DriverConstants {
    private DriverConstants() {
    }

    public static final int DRIVER_SERVICE_RANKING = Integer.MAX_VALUE / 2;
    public static final String DRIVER_SERVICE = "corda.driver:Boolean=true";
    public static final String DRIVER_SERVICE_FILTER = "(corda.driver=*)";
}
