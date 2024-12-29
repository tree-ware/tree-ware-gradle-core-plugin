package org.treeWare.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

const val SOURCE_SET_META_MODEL_DIRECTORY_PATH = "resources/tree_ware/meta_model"
const val SOURCE_SET_META_MODEL_FILE_EXTENSION = "json"

private const val GENERATED_DOC_DIRECTORY = "treeWare/doc"
private const val GENERATED_SRC_DIRECTORY = "treeWare/src"

fun getMetaModelDiagramsOutputDirectory(project: Project, sourceSetName: String): Provider<Directory> =
    project.layout.buildDirectory.dir("$GENERATED_DOC_DIRECTORY/$sourceSetName")

fun getMetaModelSourceOutputDirectory(project: Project, sourceSetName: String): Provider<Directory> =
    project.layout.buildDirectory.dir("$GENERATED_SRC_DIRECTORY/$sourceSetName")

fun getMetaModelKotlinOutputDirectory(project: Project, sourceSetName: String): Provider<Directory> =
    getMetaModelSourceOutputDirectory(project, sourceSetName).map { it.dir("kotlin") }

fun getMetaModelOpenApiSpecOutputDirectory(project: Project, sourceSetName: String): Provider<Directory> =
    project.layout.buildDirectory.dir("$GENERATED_DOC_DIRECTORY/$sourceSetName")