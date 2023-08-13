rootProject.name = "core-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // TODO #### version
            val treeWareKotlinCoreVersion = version("treeWareKotlinCoreVersion", "generate-model-classes-SNAPSHOT")
            library("treeWareKotlinCore", "org.tree-ware.tree-ware-kotlin-core", "core").versionRef(
                treeWareKotlinCoreVersion
            )
        }
    }
}