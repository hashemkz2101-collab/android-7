package ir.mahroch.tapekhash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.mahroch.tapekhash.data.Session
import ir.mahroch.tapekhash.ui.AppNavGraph
import ir.mahroch.tapekhash.ui.theme.TapeKhashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Session.loadCachedUser()

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TapeKhashTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavGraph()
                    }
                }
            }
        }
    }
}
