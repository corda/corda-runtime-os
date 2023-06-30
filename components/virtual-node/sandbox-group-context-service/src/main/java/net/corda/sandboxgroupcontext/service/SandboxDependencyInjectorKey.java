package net.corda.sandboxgroupcontext.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Target({TYPE})
@Retention(CLASS)
public @interface SandboxDependencyInjectorKey {
    String DEPENDENCY_INJECTOR = "DEPENDENCY_INJECTOR";
}