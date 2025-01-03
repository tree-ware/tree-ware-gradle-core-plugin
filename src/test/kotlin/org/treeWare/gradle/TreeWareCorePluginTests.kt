package org.treeWare.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

class TreeWareCorePluginTests {
    @Test
    fun plugin_must_register_tasks() {
        // Build a project.
        // TODO(deepak-nulu): use Gradle TestKit with a build file that includes Java/Kotlin plugins and sourceSets.
        val project: Project = ProjectBuilder.builder().build()

        // Apply the plugin-under-test.
        project.pluginManager.apply("org.tree-ware.core")

        // Verify that the plugin registered the "generateDiagrams" umbrella task.
        assertNotNull(project.tasks.findByName("generateDiagrams"))
        // TODO(deepak-nulu): Verify that the plugin registered the "generateDiagrams<SourceSet>" tasks.

        // Verify that the plugin registered the "generateKotlin" umbrella task.
        assertNotNull(project.tasks.findByName("generateKotlin"))
        // TODO(deepak-nulu): Verify that the plugin registered the "generateKotlin<SourceSet>" tasks.

        // Verify that the plugin registered the "generateOpenApiSpec" umbrella task.
        assertNotNull(project.tasks.findByName("generateOpenApiSpec"))
        // TODO(deepak-nulu): Verify that the plugin registered the "generateOpenApiSpec<SourceSet>" tasks.
    }
}