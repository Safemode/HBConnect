package com.safemode.hbconnect

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safemode.hbconnect.data.settings.ConnectionConfig
import com.safemode.hbconnect.data.settings.ThemePreference
import com.safemode.hbconnect.ui.HBConnectRoot
import com.safemode.hbconnect.ui.theme.HBConnectTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read the persisted settings synchronously once (a single small DataStore file) so the
        // very first frame - and the window background behind it - already match the saved theme.
        // Without this the first frame defaults to SYSTEM and the window stays the OS day/night
        // colour, which flashes white when the in-app theme is dark but the device is not.
        val initialConfig = runBlocking { Graph.settingsRepository.config.first() }
        handleDeepLink(intent)

        enableEdgeToEdge()
        applyWindowBackground(initialConfig.themePreference)

        setContent {
            val config by Graph.settingsRepository.config
                .collectAsStateWithLifecycle(initialValue = initialConfig)
            val preference = config.themePreference

            val darkTheme = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK, ThemePreference.MIDNIGHT -> true
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            HBConnectTheme(themePreference = preference) {
                HBConnectRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** Routes a notification tap to the item it referenced; the UI picks this up and navigates. */
    private fun handleDeepLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ENTITY_ID)?.let { Graph.requestOpenEntity(it) }
    }

    /** Paints the activity window the theme's background colour so nothing white shows before the
     *  first Compose frame, even when the chosen theme differs from the system day/night setting. */
    private fun applyWindowBackground(preference: ThemePreference) {
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val color = when (preference) {
            ThemePreference.MIDNIGHT -> Color.BLACK
            ThemePreference.DARK -> BACKGROUND_DARK
            ThemePreference.LIGHT -> BACKGROUND_LIGHT
            ThemePreference.SYSTEM -> if (systemNight) BACKGROUND_DARK else BACKGROUND_LIGHT
        }
        window.setBackgroundDrawable(ColorDrawable(color))
    }

    companion object {
        /** Intent extra carrying an entity id to open, set on a maintenance-reminder notification. */
        const val EXTRA_ENTITY_ID = "entity_id"

        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        // Match md_background_light / md_background_dark in the Compose theme.
        private val BACKGROUND_LIGHT = Color.rgb(0xFA, 0xFD, 0xFA)
        private val BACKGROUND_DARK = Color.rgb(0x19, 0x1C, 0x1B)
    }
}
