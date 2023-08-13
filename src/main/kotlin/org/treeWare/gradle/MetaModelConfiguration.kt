package org.treeWare.gradle

import org.gradle.api.provider.ListProperty

abstract class MetaModelConfiguration {
    abstract val files: ListProperty<String>
}