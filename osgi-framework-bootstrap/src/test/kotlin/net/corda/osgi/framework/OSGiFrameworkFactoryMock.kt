package net.corda.osgi.framework

import org.osgi.framework.launch.Framework
import org.osgi.framework.launch.FrameworkFactory

class OSGiFrameworkFactoryMock: FrameworkFactory {

    override fun newFramework(configurationMap: MutableMap<String, String>): Framework {
        return OSGiFrameworkMock(configurationMap)
    }

}