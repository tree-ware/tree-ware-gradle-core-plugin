package org.treeWare.gradle

import org.gradle.api.provider.ListProperty

abstract class MetaModelAuxConfiguration {
    abstract val auxClasses: ListProperty<String>
}