import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar

// plugin to cater for R3 vs Non R3 users building code base.
class PublishPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (System.getenv("CORDA_ARTIFACTORY_USERNAME") != null || project.hasProperty("cordaArtifactoryUsername")) {
            if (!project.rootProject.pluginManager.hasPlugin("com.r3.internal.gradle.plugins.r3ArtifactoryPublish")) {
                project.rootProject.pluginManager.apply("com.r3.internal.gradle.plugins.r3ArtifactoryPublish")
            }
            for (subproject in project.subprojects) {
                subproject.afterEvaluate {
                    subproject.pluginManager.withPlugin("java") {
                        if (!subproject.pluginManager.hasPlugin("net.corda.plugins.cordapp-cpk")) {
                            subproject.pluginManager.apply("com.r3.internal.gradle.plugins.r3Publish")
                        }
                    }
                }
            }
        } else {
            for (subproject in project.subprojects) {
                subproject.afterEvaluate {
                    if (!subproject.pluginManager.hasPlugin("net.corda.plugins.cordapp-cpk")) { // no need to publish these models just used for testing
                        subproject.plugins.apply(MavenPublishPlugin::class.java)
                        subproject.plugins.withType(JavaPlugin::class.java).all {
                            if ((subproject.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar).isEnabled) {
                                subproject.extensions.getByType(
                                    PublishingExtension::class.java
                                ).publications
                                    .register("maven", MavenPublication::class.java) { maven ->
                                        maven.groupId = subproject.group.toString()
                                        maven.artifactId = "corda-" + subproject.name
                                        maven.artifact(subproject.tasks.getByName(JavaPlugin.JAR_TASK_NAME))
                                        maven.artifact(subproject.tasks.getByName("javadocJar"))
                                        maven.artifact(subproject.tasks.getByName("sourcesJar"))

                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}