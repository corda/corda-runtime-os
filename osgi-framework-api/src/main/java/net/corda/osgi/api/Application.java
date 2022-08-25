package net.corda.osgi.api;

import org.jetbrains.annotations.NotNull;

/**
 * The {@code osgi-framework-bootstrap} module calls {@link #startup} of the class implementing this interface
 * as entry point of the application and {@link #shutdown} before to stop the OSGi framework.
 *
 * **NOTE:**
 * *To distribute an application as a bootable JAR built with the {@code corda.common.app} plugin,
 * only one class must implement this interface because that class is the entry point of the application.*
 *
 * The class implementing this interface must define an OSGi component and register as an OSGi service.
 *
 * **EXAMPLE**
 *
 * The code shows how to implement the {@link Application} interface and inject an OSGi service/component
 * annotated with {@code @Reference} into the constructor.
 *
 * See the {@code README.md} file of the {@code buildSrc} module for the {@code common-app} plugin for more info.
 *
 * <pre>
 * import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
 * import net.corda.osgi.api.Application
 * import org.osgi.service.component.annotations.Activate
 * import org.osgi.service.component.annotations.Component
 * import org.osgi.service.component.annotations.Reference
 *
 * &#64;Component(immediate = true)
 * class App @Activate constructor(
 *     &#64;Reference(service = KafkaTopicAdmin::class)
 *     private var kafkaTopicAdmin: KafkaTopicAdmin,
 * ): Application {
 *
 *  override fun startup(args: Array<String>) {
 *      println("startup with ${kafkaTopicAdmin}")
 *  }
 *
 *  override fun shutdown() {
 *      println("shutdown")
 *  }
 *}
 * </pre>
 */
public interface Application extends AutoCloseable {

    /**
     * Call {@link #shutdown}.
     *
     * @see AutoCloseable#close
     */
    @Override
    default void close() {
        shutdown();
    }

    /**
     * The {@code osgi-framework-bootstrap} module calls this method as entry point of the application.
     *
     * @param args passed from the OS starting the bootable JAR.
     */
    void startup(@NotNull String[] args);

    /**
     * The {@code osgi-framework-bootstrap} module calls this method before to stop the OSGi framework.
     *
     * *WARNING! Do not call {@link Shutdown} service from here because it calls this method
     * resulting in an infinite recursive loop.
     *
     * *NOTE. The module {@code osgi-framework-bootstrap} implements an experimental solution to avoid shutdown loops'.
     */
    void shutdown();

}
