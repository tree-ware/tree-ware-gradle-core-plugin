package org.treeWare.gradle

import org.gradle.api.Action
import org.gradle.api.tasks.Nested

abstract class TreeWareCorePluginExtension {
    @get:Nested
    abstract val metaModelAuxConfiguration: MetaModelAuxConfiguration

    fun metaModelAux(action: Action<in MetaModelAuxConfiguration>) {
        action.execute(metaModelAuxConfiguration)
    }
}