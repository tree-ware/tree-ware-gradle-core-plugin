package org.treeWare.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private const val TREE_WARE_TASK_GROUP = "tree-ware"

typealias ConfigureSources = (project: Project, sourceSetName: String, sources: SourceDirectorySet) -> Unit
typealias ConfigureTask<T> = (task: T, sourceSetName: String, resources: SourceDirectorySet) -> Unit

class TreeWareCorePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        registerTasks(
            project, "generateDiagrams", GenerateDiagramsTask::class.java, null
        ) { task, sourceSetName, resources ->
            val outputDirectory = getMetaModelDiagramsOutputDirectory(project, sourceSetName)
            task.resources.set(resources)
            task.outputDirectory.set(outputDirectory)
        }

        registerTasks(
            project, "generateKotlin", GenerateKotlinTask::class.java, ::addGeneratedKotlinToSourceSet
        ) { task, sourceSetName, resources ->
            val outputDirectory = getMetaModelKotlinOutputDirectory(project, sourceSetName)
            task.resources.set(resources)
            task.outputDirectory.set(outputDirectory)
        }

        // Make KotlinCompile tasks depend on the GenerateKotlin tasks.
        // The GenerateKotlin tasks are added lazily, so they are added as dependencies when they become available.
        project.tasks.withType(GenerateKotlinTask::class.java) { generateTask ->
            project.tasks.withType(KotlinCompile::class.java) { compileTask ->
                compileTask.dependsOn(generateTask)
            }
        }
    }

    private fun <T : Task> registerTasks(
        project: Project,
        taskName: String,
        taskClass: Class<T>,
        configureSources: ConfigureSources?,
        configureTask: ConfigureTask<T>
    ) {
        // NOTE: the umbrella-task is being created rather than registered in order to get a Task rather than a
        // TaskProvider. This allows the dependsOn() method to be called on the umbrella task to make it depend on the
        // sourceSet-specific tasks (which are registered rather than created).
        val umbrellaTask = project.tasks.create(taskName) { it.group = TREE_WARE_TASK_GROUP }

        val kotlinExtension = project.extensions.findByName("kotlin") as? KotlinMultiplatformExtension
        if (kotlinExtension != null) {
            // Register tasks for Kotlin multiplatform plugin sourceSets
            val kotlinSourceSets = kotlinExtension.sourceSets
            kotlinSourceSets.all {
                if (configureSources != null) configureSources(project, it.name, it.kotlin)
                registerSourceSetTasks(taskName, taskClass, configureTask, umbrellaTask, project, it.name, it.resources)
            }
        } else {
            // Register tasks for Java/Kotlin plugin sourceSets
            val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
            sourceSets?.all {
                if (configureSources != null) configureSources(project, it.name, it.java)
                registerSourceSetTasks(taskName, taskClass, configureTask, umbrellaTask, project, it.name, it.resources)
            }
        }
    }

    private fun <T : Task> registerSourceSetTasks(
        taskName: String,
        taskClass: Class<T>,
        configureTask: ConfigureTask<T>,
        umbrellaTask: Task,
        project: Project,
        sourceSetName: String,
        resources: SourceDirectorySet
    ) {
        val taskSuffix = sourceSetName.replaceFirstChar { it.uppercase() }
        val task = project.tasks.register("$taskName$taskSuffix", taskClass) {
            it.group = TREE_WARE_TASK_GROUP
            configureTask(it, sourceSetName, resources)
        }
        umbrellaTask.dependsOn(task)
    }
}

fun addGeneratedKotlinToSourceSet(project: Project, sourceSetName: String, sources: SourceDirectorySet) {
    val outputDirectory = getMetaModelSourceOutputDirectory(project, sourceSetName)
    sources.srcDir(outputDirectory)
}