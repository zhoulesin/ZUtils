package com.zhoulesin.zutils.plugin

import android.content.Context
import android.os.Build
import android.util.Log
import com.zhoulesin.zutils.engine.core.ZFunction
import com.zhoulesin.zutils.engine.dex.DependencySpec
import com.zhoulesin.zutils.engine.dex.DexLoader
import com.zhoulesin.zutils.engine.dex.DexSpec
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.nio.ByteBuffer

private const val TAG = "ZUtils-DEX"

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
    private val remoteBaseUrl: String? = null,
) : DexLoader {

    private var specMap: Map<String, DexSpec>? = null
    private val mutex = Any()

    private suspend fun ensureSpecsLoaded(): Map<String, DexSpec> {
        specMap?.let { return it }
        return synchronized(mutex) {
            specMap?.let { return@synchronized it }
            val manifestUrl = if (remoteBaseUrl != null) "$remoteBaseUrl/manifest.json" else "assets://dex/dex_manifest.json"
            try {
                val json = withContext(Dispatchers.IO) {
                    if (remoteBaseUrl != null) {
                        URL("$remoteBaseUrl/manifest.json").readText()
                    } else {
                        context.assets.open("dex/dex_manifest.json").bufferedReader().readText()
                    }
                }
                val manifest = Json.decodeFromString<DexManifest>(json)
                val map = manifest.plugins.map { p ->
                    DexSpec(
                        functionName = p.functionName,
                        dexUrl = p.dexUrl,
                        className = p.className,
                        version = p.version,
                        dependencies = p.dependencies.map { d ->
                            DependencySpec(name = d.name, dexUrl = d.dexUrl, version = d.version)
                        },
                    )
                }.associateBy { it.functionName }
                specMap = map
                map
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load manifest from $manifestUrl", e)
                throw e
            }
        }
    }

    override suspend fun resolve(functionName: String): DexSpec? = ensureSpecsLoaded()[functionName]

    private fun readBytes(path: String): ByteArray {
        val url = if (remoteBaseUrl != null) "$remoteBaseUrl/$path" else "assets://$path"
        return try {
            if (remoteBaseUrl != null) {
                URL("$remoteBaseUrl/$path").openStream().readBytes()
            } else {
                context.assets.open(path).readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read DEX from $url", e)
            throw e
        }
    }

    override suspend fun download(spec: DexSpec): ByteArray = withContext(Dispatchers.IO) {
        Log.i(TAG, "Downloading ${spec.functionName} v${spec.version} from ${spec.dexUrl}")
        readBytes(spec.dexUrl).also {
            Log.i(TAG, "Downloaded ${spec.functionName}: ${it.size / 1024}KB")
        }
    }

    override fun load(dexBytes: ByteArray, spec: DexSpec): List<ZFunction> {
        Log.i(TAG, "Loading ${spec.className}...")
        val allBytes = mutableListOf(dexBytes)
        for (dep in spec.dependencies) {
            allBytes.add(readBytes(dep.dexUrl))
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

        return try {
            val clazz = loader.loadClass(spec.className)
            val instance = clazz.getDeclaredConstructor().newInstance() as ZFunction
            Log.i(TAG, "Loaded ${spec.functionName} from ${spec.className}")
            listOf(instance)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate ${spec.className}", e)
            throw e
        }
    }

    override fun getCacheDir(): File = File(context.cacheDir, "dex_cache")
}
