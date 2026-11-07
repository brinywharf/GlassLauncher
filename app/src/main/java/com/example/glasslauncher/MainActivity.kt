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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { LauncherApp(homePressed = homePressed) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.tryEmit(Unit)
    }

    // A launcher should never close on Back.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
}
