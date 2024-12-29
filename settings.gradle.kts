rootProject.name = "core-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val treeWareKotlinCoreVersion = version("treeWareKotlinCoreVersion", "0.3.0.2")
            library("treeWareKotlinCore", "org.tree-ware.tree-ware-kotlin-core", "core").versionRef(
                treeWareKotlinCoreVersion
            )
        }
    }
}