package com.zhoulesin.zutils.engine.dex

import com.zhoulesin.zutils.engine.core.Parameter

data class DependencySpec(
    val name: String,
    val dexUrl: String,
    val version: String = "1.0",
    val checksum: String = "",
    val signature: String = "",
)

data class DexSpec(
    val functionName: String,
    val description: String = "",
    val parameters: List<Parameter> = emptyList(),
    val dexUrl: String,
    val className: String,
    val version: String = "1.0",
    val checksum: String = "",
    val signature: String = "",
    val signatureAlgorithm: String = "SHA256withRSA",
    val requiredPermissions: List<String> = emptyList(),
    val dependencies: List<DependencySpec> = emptyList(),
)
