package org.treeWare.gradle

import okio.FileSystem
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.treeWare.metaModel.encoder.encodeDot
import org.treeWare.metaModel.newMetaModelFromJsonFiles

abstract class GenerateDiagramsTask : DefaultTask() {
    @get:Input
    abstract val metaModelConfiguration: Property<MetaModelConfiguration>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val result = newMetaModelFromJsonFiles(
            metaModelConfiguration.get().files.get(),
            false,
            null,
            null,
            emptyList(),
            true,
            FileSystem.SYSTEM
        )
        val metaModel = result.metaModel
        if (metaModel == null) {
            logger.error("Errors while building meta-model:")
            result.errors.forEach { logger.error(it) }
            return
        }
        val directoryPath = outputDirectory.get().toString()
        logger.info("Generating diagrams in $directoryPath")
        encodeDot(metaModel, directoryPath)
    }
}