package com.zhoulesin.zutils.plugin

import android.content.Context
import android.os.Build
import android.util.Log
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.FunctionSource
import com.zhoulesin.zutils.engine.core.OutputType
import com.zhoulesin.zutils.engine.core.Parameter
import com.zhoulesin.zutils.engine.core.ParameterType
import com.zhoulesin.zutils.engine.core.ZFunction
import com.zhoulesin.zutils.engine.bridge.ApiBridge
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
data class ManifestParam(
    val name: String,
    val description: String = "",
    val type: String = "STRING",
    val required: Boolean = false,
)

@Serializable
data class ManifestPlugin(
    val functionName: String,
    val description: String = "",
    val version: String,
    val dexUrl: String,
    val className: String,
    val parameters: List<ManifestParam> = emptyList(),
    val dependencies: List<ManifestDep> = emptyList(),
)

@Serializable
data class DexManifest(
    val plugins: List<ManifestPlugin>,
)

class DefaultDexLoader(
    private val context: Context,
    private val remoteBaseUrl: String,
) : DexLoader {

    private var specMap: Map<String, DexSpec>? = null
    private val mutex = Any()

    private suspend fun fetchManifestText(): String = withContext(Dispatchers.IO) {
        URL("$remoteBaseUrl/manifest.json").readText()
    }

    private suspend fun ensureSpecsLoaded(): Map<String, DexSpec> {
        specMap?.let { return it }
        val json = try {
            fetchManifestText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load manifest from $remoteBaseUrl/manifest.json", e)
            throw e
        }
        return synchronized(mutex) {
            specMap?.let { return@synchronized it }
            val manifest = Json.decodeFromString<DexManifest>(json)
            val map = manifest.plugins.map { p ->
                Log.i(TAG, "Manifest plugin: ${p.functionName} description='${p.description}' params=${p.parameters.size}")
                for (mp in p.parameters) {
                    Log.i(TAG, "   param: ${mp.name} type=${mp.type} required=${mp.required} desc='${mp.description}'")
                }
                DexSpec(
                    functionName = p.functionName,
                    description = p.description,
                    parameters = p.parameters.map { mp ->
                        Parameter(
                            name = mp.name,
                            description = mp.description,
                            type = try { ParameterType.valueOf(mp.type) } catch (_: Exception) { ParameterType.STRING },
                            required = mp.required,
                        )
                    },
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
        }
    }

    override suspend fun resolve(functionName: String): DexSpec? = ensureSpecsLoaded()[functionName]

    private fun readBytes(path: String): ByteArray {
        return try {
            URL("$remoteBaseUrl/$path").openStream().readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read DEX from $remoteBaseUrl/$path", e)
            throw e
        }
    }

    private fun cacheFile(spec: DexSpec): File = File(getCacheDir(), "${spec.functionName}_${spec.version}.dex")

    private fun depCacheFile(dep: DependencySpec): File = File(getCacheDir(), "${dep.name}_${dep.version}.dex")

    override suspend fun download(spec: DexSpec): ByteArray = withContext(Dispatchers.IO) {
        val cache = cacheFile(spec)
        if (cache.exists()) {
            Log.i(TAG, "Cache hit for ${spec.functionName} v${spec.version}")
            return@withContext cache.readBytes()
        }
        Log.i(TAG, "Downloading ${spec.functionName} v${spec.version} from ${spec.dexUrl}")
        val bytes = readBytes(spec.dexUrl)
        getCacheDir().mkdirs()
        cache.writeBytes(bytes)
        Log.i(TAG, "Cached to ${cache.name} (${bytes.size / 1024}KB)")
        for (dep in spec.dependencies) {
            val depCache = depCacheFile(dep)
            if (!depCache.exists()) {
                Log.i(TAG, "Downloading dep ${dep.name} v${dep.version} from ${dep.dexUrl}")
                val depBytes = readBytes(dep.dexUrl)
                depCache.writeBytes(depBytes)
                Log.i(TAG, "Cached dep ${depCache.name}")
            }
        }
        bytes
    }

    override fun load(dexBytes: ByteArray, spec: DexSpec): List<ZFunction> {
        Log.i(TAG, "Loading ${spec.className}...")
        val allBytes = mutableListOf(dexBytes)
        for (dep in spec.dependencies) {
            val depCache = depCacheFile(dep)
            allBytes.add(if (depCache.exists()) depCache.readBytes() else readBytes(dep.dexUrl))
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
            val instance = clazz.getDeclaredConstructor().newInstance()

            // Inject ApiBridge if the DEX class supports it
            try {
                val setBridge = clazz.getMethod("setApiBridge", Any::class.java)
                val bridgeClass = Class.forName("com.zhoulesin.zutils.bridge.AppApiBridge")
                val bridge = bridgeClass.getField("INSTANCE").get(null)
                setBridge.invoke(instance, bridge)
                Log.i(TAG, "ApiBridge injected into ${spec.className}")
            } catch (_: NoSuchMethodException) { /* not a bridge DEX, ignore */ }

            val function = when {
                instance is ZFunction -> {
                    Log.i(TAG, "Loaded ZFunction ${spec.functionName} from ${spec.className}")
                    instance
                }
                hasHandleMethod(clazz) -> {
                    Log.i(TAG, "Loaded handle-protocol function ${spec.functionName} from ${spec.className}")
                    DexFunctionAdapter(instance, spec)
                }
                else -> {
                    throw ClassCastException("${spec.className} does not implement ZFunction or handle(String)String")
                }
            }
            listOf(function)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate ${spec.className}", e)
            throw e
        }
    }

    override suspend fun getAllPluginInfos(): List<FunctionInfo> {
        ensureSpecsLoaded()
        return specMap?.values?.map { spec ->
            FunctionInfo(
                name = spec.functionName,
                description = spec.description,
                parameters = spec.parameters,
                source = FunctionSource.DEX,
                outputType = OutputType.OBJECT,
            )
        } ?: emptyList()
    }

    override fun getCacheDir(): File = File(context.cacheDir, "dex_cache")

    override suspend fun refresh() {
        synchronized(mutex) { specMap = null }
        ensureSpecsLoaded()
    }

    private fun hasHandleMethod(clazz: Class<*>): Boolean {
        return try {
            clazz.getMethod("handle", String::class.java)
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }
}
