package net.corda.kotlin.reflect.heap;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings({ "unused", "unchecked" })
public final class KotlinHeapFixer {
    private static final Logger LOG = LoggerFactory.getLogger(KotlinHeapFixer.class);

    private static final String MODULE_BY_CLASSLOADER = "kotlin.reflect.jvm.internal.ModuleByClassLoaderKt";
    private static final String WEAK_CLASSLOADER_BOX = "kotlin.reflect.jvm.internal.WeakClassLoaderBox";
    private static final long INITIAL_DELAY = 10L;
    private static final long REPEAT_DELAY = 5L;

    private static String getKotlinVersion() {
        try (InputStream input = KotlinHeapFixer.class.getResourceAsStream("configuration.properties")) {
            final Properties props = new Properties();
            props.load(input);
            return props.getProperty("kotlinVersion", "<unknown>");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Thread createThread(Runnable runnable) {
        final Thread thread = new Thread(runnable, "kotlin-" + getKotlinVersion() + "-sink-plunger");
        thread.setDaemon(true);
        return thread;
    }

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(KotlinHeapFixer::createThread);
    private static final Map<?, WeakReference<?>> moduleByClassLoader;
    private static final MethodHandle reference;

    static {
        Map<?, WeakReference<?>> map;
        MethodHandle ref;
        try {
            final Class<?> kotlinClass = Class.forName(MODULE_BY_CLASSLOADER, true, KotlinHeapFixer.class.getClassLoader());
            final Lookup myLookup = MethodHandles.lookup();
            final Lookup mapLookup = MethodHandles.privateLookupIn(kotlinClass, myLookup);
            final MethodHandle handle = mapLookup.findStaticGetter(kotlinClass, "moduleByClassLoader", ConcurrentMap.class);
            map = (ConcurrentMap<?, WeakReference<?>>) handle.invokeExact();

            final Class<?> keyClass = Class.forName(WEAK_CLASSLOADER_BOX, false, kotlinClass.getClassLoader());
            final Lookup refLookup = MethodHandles.privateLookupIn(keyClass, myLookup);
            ref = refLookup.findGetter(keyClass, "ref", WeakReference.class);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            LOG.warn("Failed to apply heap fix for Kotlin Reflection" , t);
            map = emptyMap();
            ref = null;
        }

        moduleByClassLoader = map;
        reference = ref;
    }

    private static boolean hasNullReference(Object key) {
        try {
            final WeakReference<?> ref = (WeakReference<?>) reference.invoke(key);
            return ref.get() == null;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void flush() {
        moduleByClassLoader.entrySet().removeIf(entry ->
            (entry.getValue().get() == null) || hasNullReference(entry.getKey())
        );
    }

    public static void start() {
        executor.scheduleWithFixedDelay(KotlinHeapFixer::flush, INITIAL_DELAY, REPEAT_DELAY, SECONDS);
    }

    public static void stop() {
        executor.shutdown();
    }
}
