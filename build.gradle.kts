import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.tree-ware.tree-ware-gradle-core-plugin"
version = "0.5.2.0"

plugins {
    kotlin("jvm") version "2.1.10"
    id("idea")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.2.1"
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(libs.treeWareKotlinCore)
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")

    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("treeWareCorePlugin") {
            id = "org.tree-ware.core"
            displayName = "Tree-Ware Core Plugin"
            description = "A plugin for generating diagrams and model-classes from tree-ware meta-models"
            implementationClass = "org.treeWare.gradle.TreeWareCorePlugin"

            tags.set(listOf("tree-ware", "tree-ware-core"))
            website.set("https://www.tree-ware.org")
            vcsUrl.set("https://github.com/tree-ware/tree-ware-gradle-core-plugin")
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