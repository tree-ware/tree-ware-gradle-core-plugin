package org.treeWare.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val TREE_WARE_TASK_GROUP = "tree-ware"

typealias ConfigureTask<T> = (task: T, sourceSetName: String, resources: SourceDirectorySet) -> Unit

class TreeWareCorePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        registerTasks(project, "generateDiagrams", GenerateDiagramsTask::class.java) { task, sourceSetName, resources ->
            task.resources.set(resources)
            task.outputDirectory.set(
                project.layout.buildDirectory.dir("$META_MODEL_DIAGRAMS_OUTPUT_DIRECTORY/$sourceSetName")
            )
        }

        registerTasks(project, "generateKotlin", GenerateKotlinTask::class.java) { task, sourceSetName, resources ->
            val outputDirectory = project.layout.buildDirectory.dir("$META_MODEL_KOTLIN_OUTPUT_DIRECTORY/$sourceSetName/kotlin")
            task.resources.set(resources)
            task.outputDirectory.set(outputDirectory)
        }
    }

    private fun <T : Task> registerTasks(
        project: Project,
        taskName: String,
        taskClass: Class<T>,
        configureTask: ConfigureTask<T>
    ) {
        // NOTE: the umbrella-task is being created rather than registered in order to get a Task rather than a
        // TaskProvider. This allows the dependsOn() method to be called on the umbrella task to make it depend on the
        // sourceSet-specific tasks (which are registered rather than created).
        val umbrellaTask = project.tasks.create(taskName) { it.group = TREE_WARE_TASK_GROUP }

        // Register tasks for Java/Kotlin plugin sourceSets
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
        sourceSets?.all {
            registerSourceSetTasks(taskName, taskClass, configureTask, umbrellaTask, project, it.name, it.resources)
        }

        // Register tasks for Kotlin multiplatform plugin sourceSets
        val kotlinExtension = project.extensions.findByName("kotlin") as? KotlinMultiplatformExtension
        val kotlinSourceSets = kotlinExtension?.sourceSets
        kotlinSourceSets?.all {
            registerSourceSetTasks(taskName, taskClass, configureTask, umbrellaTask, project, it.name, it.resources)
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