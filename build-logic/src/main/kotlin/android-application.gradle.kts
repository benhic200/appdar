import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<AppExtension> {
                compileSdk = 34
                defaultConfig {
                    minSdk = 24
                    targetSdk = 34
                }
            }
        }
    }
}