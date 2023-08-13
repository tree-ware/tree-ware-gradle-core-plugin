package org.treeWare.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class TreeWareCorePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("treeWare", TreeWareCorePluginExtension::class.java)

        project.tasks.register("generateDiagrams", GenerateDiagramsTask::class.java) {
            it.group = "tree-ware"
            it.metaModelConfiguration.set(extension.metaModelConfiguration)
            it.outputDirectory.set(
                project.layout.buildDirectory.dir("tree-ware/diagrams")
            )
        }
    }
}