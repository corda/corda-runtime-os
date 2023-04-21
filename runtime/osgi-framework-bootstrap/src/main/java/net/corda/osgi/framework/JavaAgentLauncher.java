package net.corda.osgi.framework;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

final class JavaAgentLauncher {

    public static void premain(String agentArguments, Instrumentation instrumentation) throws Exception {
        final ClassLoader cl = JavaAgentLauncher.class.getClassLoader();
        Enumeration<URL> resources = cl.getResources("META-INF/javaAgents.properties");
        while (resources.hasMoreElements()) {
            final Properties properties = new Properties();
            try (InputStream input = resources.nextElement().openStream()) {
                properties.load(input);
            }
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                String agentClassName = (String) entry.getKey();
                String agentArgs = (String) entry.getValue();
                Class<?> agentClass = Class.forName(agentClassName, false, cl);
                Method premainMethod = agentClass.getMethod("premain", String.class, Instrumentation.class);
                premainMethod.invoke(null, agentArgs, instrumentation);
            }
        }
    }

    public static void agentmain(String agentArguments, Instrumentation instrumentation) throws Exception {
        premain(agentArguments, instrumentation);
    }
}
