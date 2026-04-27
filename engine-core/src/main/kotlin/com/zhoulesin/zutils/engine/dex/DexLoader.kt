package com.zhoulesin.zutils.engine.dex

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.ZFunction

interface DexLoader {
    suspend fun resolve(functionName: String): DexSpec?
    suspend fun download(spec: DexSpec): ByteArray
    fun load(dexBytes: ByteArray, spec: DexSpec): List<ZFunction>
    suspend fun getAllPluginInfos(): List<FunctionInfo>
    fun getCacheDir(): java.io.File
}
