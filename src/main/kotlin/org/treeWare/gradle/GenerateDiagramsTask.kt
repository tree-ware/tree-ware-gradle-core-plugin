package org.treeWare.gradle

import okio.FileSystem
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.treeWare.metaModel.encoder.encodeDot
import org.treeWare.metaModel.newMetaModelFromJsonFiles

abstract class GenerateDiagramsTask : DefaultTask() {
    @get:Input
    abstract val resources: Property<SourceDirectorySet>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val metaModelFilePaths = resources.get().filter {
            it.parent.endsWith(SOURCE_SET_META_MODEL_DIRECTORY_PATH) &&
                    it.extension == SOURCE_SET_META_MODEL_FILE_EXTENSION
        }.files.map { it.path }
        val result = newMetaModelFromJsonFiles(
            metaModelFilePaths,
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