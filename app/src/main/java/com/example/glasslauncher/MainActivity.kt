package com.example.glasslauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.glasslauncher.ui.LauncherApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    /** Fires when the user presses Home while the launcher is already in front. */
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var widgets: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgets = WidgetController(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { LauncherApp(homePressed = homePressed, widgets = widgets) }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgets.host.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { widgets.host.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.tryEmit(Unit)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }
}
