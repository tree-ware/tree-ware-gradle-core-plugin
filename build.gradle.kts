import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.tree-ware.tree-ware-gradle-core-plugin"
version = "0.1.0.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.7.0"
    id("idea")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.2.0"
    id("maven-publish")
}

repositories {
    mavenLocal() // TODO #### delete
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

tasks.withType<KotlinCompile> {
    // Compile for Java 8 (default is Java 6)
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    implementation(libs.treeWareKotlinCore)
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.22")

    testImplementation(kotlin("test"))
}

pluginBundle {
    website = "https://www.tree-ware.org"
    vcsUrl = "https://github.com/tree-ware/tree-ware-gradle-core-plugin"
    tags = listOf("tree-ware", "tree-ware-core")
}
gradlePlugin {
    plugins {
        create("treeWareCorePlugin") {
            id = "org.tree-ware.core"
            displayName = "Tree-Ware Core Plugin"
            description = "A plugin for generating diagrams and model-classes from tree-ware meta-models"
            implementationClass = "org.treeWare.gradle.TreeWareCorePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}