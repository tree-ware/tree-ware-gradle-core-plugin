rootProject.name = "core-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val treeWareKotlinCoreVersion = version("treeWareKotlinCoreVersion", "0.5.1.0-SNAPSHOT")
            library("treeWareKotlinCore", "org.tree-ware.tree-ware-kotlin-core", "core").versionRef(
                treeWareKotlinCoreVersion
            )
        }
    }
}