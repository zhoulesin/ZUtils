package com.zhoulesin.zutils.plugin

import android.content.Context
import android.os.Build
import com.zhoulesin.zutils.engine.core.ZFunction
import com.zhoulesin.zutils.engine.dex.DependencySpec
import com.zhoulesin.zutils.engine.dex.DexLoader
import com.zhoulesin.zutils.engine.dex.DexSpec
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer

@Serializable
data class ManifestDep(
    val name: String,
    val version: String,
    val dexUrl: String,
)

@Serializable
data class ManifestPlugin(
    val functionName: String,
    val version: String,
    val dexUrl: String,
    val className: String,
    val dependencies: List<ManifestDep> = emptyList(),
)

@Serializable
data class DexManifest(
    val plugins: List<ManifestPlugin>,
)

class DefaultDexLoader(
    private val context: Context,
) : DexLoader {

    private val specs: List<DexSpec> by lazy {
        val json = context.assets.open("dex/dex_manifest.json").bufferedReader().readText()
        val manifest = Json.decodeFromString<DexManifest>(json)
        manifest.plugins.map { p ->
            DexSpec(
                functionName = p.functionName,
                dexUrl = p.dexUrl,
                className = p.className,
                version = p.version,
                dependencies = p.dependencies.map { d ->
                    DependencySpec(
                        name = d.name,
                        dexUrl = d.dexUrl,
                        version = d.version,
                    )
                },
            )
        }
    }

    private val specMap by lazy { specs.associateBy { it.functionName } }

    override suspend fun resolve(functionName: String): DexSpec? = specMap[functionName]

    override suspend fun download(spec: DexSpec): ByteArray {
        return context.assets.open(spec.dexUrl).readBytes()
    }

    override fun load(dexBytes: ByteArray, spec: DexSpec): List<ZFunction> {
        val allBytes = mutableListOf(dexBytes)
        for (dep in spec.dependencies) {
            allBytes.add(context.assets.open(dep.dexUrl).readBytes())
        }

        val loader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val buffers = allBytes.map { ByteBuffer.wrap(it) }.toTypedArray()
            InMemoryDexClassLoader(buffers, context.classLoader)
        } else {
            val cacheDir = getCacheDir().also { it.mkdirs() }
            val paths = mutableListOf<String>()
            for ((i, bytes) in allBytes.withIndex()) {
                val f = File(cacheDir, "dex_$i.dex")
                f.writeBytes(bytes)
                paths.add(f.absolutePath)
            }
            val optDir = File(cacheDir, "opt").also { it.mkdirs() }
            DexClassLoader(paths.joinToString(":"), optDir.absolutePath, null, context.classLoader)
        }

        val clazz = loader.loadClass(spec.className)
        val instance = clazz.getDeclaredConstructor().newInstance() as ZFunction
        return listOf(instance)
    }

    override fun getCacheDir(): File = File(context.cacheDir, "dex_cache")
}
