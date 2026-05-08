package com.zhoulesin.zoffice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhoulesin.zoffice.ui.OfficeRootScreen
import com.zhoulesin.zoffice.ui.theme.OfficeTheme
import com.zhoulesin.zutils.ZUtilsEngineBootstrap

/**
 * ZOffice 产品入口：界面仅在 `com.zhoulesin.zoffice.ui` 下实现，与 :app 不共用 Compose 界面。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = ZUtilsEngineBootstrap.bootstrap(this)
        setContent {
            OfficeTheme {
                OfficeRootScreen(engine = engine)
            }
        }
    }
}
