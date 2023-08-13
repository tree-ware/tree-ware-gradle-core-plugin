package org.treeWare.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertTrue


class TreeWareCorePluginTests {
    @Test
    fun plugin_must_register_generate_diagrams_task() {
        val project: Project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.tree-ware.core")

        val treeWareExtension = project.extensions.getByName("treeWare") as TreeWareCorePluginExtension
        treeWareExtension.metaModel {
            it.files.add("meta-model.json")
        }

        assertTrue(project.tasks.getByName("generateDiagrams") is GenerateDiagramsTask)
    }
}