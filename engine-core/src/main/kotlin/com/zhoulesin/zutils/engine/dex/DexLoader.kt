package com.zhoulesin.zutils.engine.dex

import com.zhoulesin.zutils.engine.core.ZFunction

interface DexLoader {
    suspend fun resolve(functionName: String): DexSpec?
    suspend fun download(spec: DexSpec): ByteArray
    fun load(dexBytes: ByteArray, spec: DexSpec): List<ZFunction>
    fun getCacheDir(): java.io.File
}
