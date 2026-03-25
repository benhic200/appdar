import org.gradle.api.Plugin
import org.gradle.api.Project

class DaggerHiltConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("com.google.dagger.hilt.android")
        }
    }
}