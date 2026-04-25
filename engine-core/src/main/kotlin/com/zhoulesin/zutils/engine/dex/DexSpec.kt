package com.zhoulesin.zutils.engine.dex

data class DependencySpec(
    val name: String,
    val dexUrl: String,
    val version: String = "1.0",
    val checksum: String = "",
)

data class DexSpec(
    val functionName: String,
    val dexUrl: String,
    val className: String,
    val version: String = "1.0",
    val checksum: String = "",
    val requiredPermissions: List<String> = emptyList(),
    val dependencies: List<DependencySpec> = emptyList(),
)
