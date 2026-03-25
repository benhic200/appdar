import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

class KotlinAndroidConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("org.jetbrains.kotlin.android")
            
            // Configure Kotlin options
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions> {
                jvmTarget = "17"
            }
        }
    }
}