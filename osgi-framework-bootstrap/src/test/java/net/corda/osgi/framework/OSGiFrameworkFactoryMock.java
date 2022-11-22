package net.corda.osgi.framework;

import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import java.util.Map;

public final class OSGiFrameworkFactoryMock implements FrameworkFactory {

    @Override
    public Framework newFramework(Map<String, String> configurationMap) {
        return new OSGiFrameworkMock(configurationMap);
    }

}