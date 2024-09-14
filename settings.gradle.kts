rootProject.name = "core-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // TODO #### drop -SNAPSHOT
            val treeWareKotlinCoreVersion = version("treeWareKotlinCoreVersion", "0.3.0.0-SNAPSHOT")
            library("treeWareKotlinCore", "org.tree-ware.tree-ware-kotlin-core", "core").versionRef(
                treeWareKotlinCoreVersion
            )
        }
    }
}