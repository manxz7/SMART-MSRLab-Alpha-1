allprojects {
    repositories {
        google()
        mavenCentral()
        // DJI Mobile SDK v4 repository (supports Mini 2)
        maven { url = uri("https://artifactory.djiits.com/artifactory/ams-android-sdk/") }
        maven { url = uri("https://jcenter.bintray.com") } // fallback for older DJI packages
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
