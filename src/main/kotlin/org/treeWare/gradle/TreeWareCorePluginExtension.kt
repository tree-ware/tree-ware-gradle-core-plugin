package org.treeWare.gradle

import org.gradle.api.Action
import org.gradle.api.tasks.Nested

abstract class TreeWareCorePluginExtension {
    @get:Nested
    abstract val metaModelConfiguration: MetaModelConfiguration

    fun metaModel(action: Action<in MetaModelConfiguration>) {
        action.execute(metaModelConfiguration)
    }
}