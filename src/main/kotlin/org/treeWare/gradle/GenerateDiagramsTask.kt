package org.treeWare.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.treeWare.metaModel.encoder.encodeDot

abstract class GenerateDiagramsTask : DefaultTask() {
    @get:Input
    abstract val resources: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val metaModelFilePaths = resources.get()
        val metaModel = getMetaModel(metaModelFilePaths, logger) ?: return
        val directoryPath = outputDirectory.get().toString()
        logger.info("Generating diagrams in $directoryPath")
        encodeDot(metaModel, directoryPath)
    }
}