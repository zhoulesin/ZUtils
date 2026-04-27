package com.zhoulesin.zutils.data

import android.content.Context
import com.zhoulesin.zutils.engine.core.FunctionRegistry
import com.zhoulesin.zutils.engine.dex.DexLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PluginInstallRepo(context: Context) {
    private val dao = DatabaseProvider.get(context).installedPluginDao()

    suspend fun getAll(): List<InstalledPluginEntity> = dao.loadAll()

    suspend fun save(functionName: String, version: String, className: String, parametersJson: String = "") {
        dao.insert(InstalledPluginEntity(
            functionName = functionName,
            version = version,
            className = className,
            parametersJson = parametersJson,
        ))
    }

    suspend fun remove(functionName: String) = dao.delete(functionName)

    suspend fun loadCachedPlugins(loader: DexLoader, registry: FunctionRegistry) = withContext(Dispatchers.IO) {
        val installed = dao.loadAll()
        for (entity in installed) {
            if (registry.contains(entity.functionName)) continue
            try {
                val spec = loader.resolve(entity.functionName) ?: continue
                val bytes = loader.download(spec)
                val functions = loader.load(bytes, spec)
                functions.forEach { registry.register(it) }
                android.util.Log.i("PluginInstallRepo", "Loaded cached ${entity.functionName} v${entity.version}")
            } catch (e: Exception) {
                android.util.Log.w("PluginInstallRepo", "Failed to load cached ${entity.functionName}", e)
            }
        }
    }
}
