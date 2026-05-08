package com.zhoulesin.zutils

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhoulesin.zutils.ui.theme.ZUtilsTheme

/**
 * ZUtils 基础能力演示壳。共享业务与引擎注册见 `:application-shell`（`application-shell/`）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = ZUtilsEngineBootstrap.bootstrap(this)
        setContent {
            ZUtilsTheme {
                MainScreen(engine = engine, topBarTitle = "ZUtils")
            }
        }
    }
}
