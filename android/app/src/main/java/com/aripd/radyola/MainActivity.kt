package com.aripd.radyola

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aripd.radyola.ui.RadyolaScreen
import com.aripd.radyola.ui.theme.RadyolaTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* isteğe bağlı */ }

    /** İzin bir kez sorulur; çal/durdur döngüsünde tekrar tekrar sorulmaz. */
    private var notificationPermissionAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RadyolaTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Android 13+ bildirim izni açılışta değil ilk çalmada istenir:
                // soru, medya bildiriminin ne işe yaradığı görüldüğü anda
                // anlamlı. Reddedilse de çalma sürer, yalnız bildirim görünmez.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LaunchedEffect(state.current != null) {
                        if (state.current != null && !notificationPermissionAsked) {
                            notificationPermissionAsked = true
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                RadyolaScreen(state = state, viewModel = viewModel)
            }
        }
    }
}
