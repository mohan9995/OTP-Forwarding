package com.otpforwarder.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otpforwarder.app.ui.components.AppLockOverlay
import com.otpforwarder.app.ui.components.BiometricPromptHelper
import com.otpforwarder.app.ui.navigation.Screen
import com.otpforwarder.app.ui.screens.LogsScreen
import com.otpforwarder.app.ui.screens.MainScreen
import com.otpforwarder.app.ui.screens.RulesScreen
import com.otpforwarder.app.ui.theme.OtpForwardingTheme
import com.otpforwarder.app.ui.viewmodel.MainViewModel
import com.otpforwarder.app.ui.viewmodel.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as OtpForwarderApp
        MainViewModelFactory(app.settingsRepository, app.logRepository)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshSystemState(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Check if there was a previous unhandled crash and alert the user
        checkPreviousCrash()

        try {
            setContent {
                OtpForwardingTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    var currentScreen by rememberSaveable { mutableStateOf(Screen.Home.route) }

                    LaunchedEffect(Unit) {
                        viewModel.refreshSystemState(this@MainActivity)
                    }

                    // If app is locked via Biometric lock, render overlay
                    if (uiState.isAppLocked) {
                        AppLockOverlay(
                            onUnlockClicked = { promptUnlock() }
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar {
                                    Screen.items.forEach { screen ->
                                        val isSelected = currentScreen == screen.route
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentScreen = screen.route },
                                            icon = {
                                                Icon(
                                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                    contentDescription = screen.title
                                                )
                                            },
                                            label = { Text(screen.title) }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            when (currentScreen) {
                                Screen.Home.route -> MainScreen(
                                    state = uiState,
                                    viewModel = viewModel,
                                    onRequestPermissions = { requestSmsPermissions() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                                Screen.Rules.route -> RulesScreen(
                                    state = uiState,
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(innerPadding)
                                )
                                Screen.Logs.route -> LogsScreen(
                                    state = uiState,
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("MainActivity", "Fatal initialization error in setContent", t)
            renderCrashFallback(t)
        }
    }

    private fun checkPreviousCrash() {
        try {
            val prefs = getSharedPreferences("crash_logs", Context.MODE_PRIVATE)
            val lastCrash = prefs.getString("last_crash", null)
            val crashTime = prefs.getLong("crash_timestamp", 0)
            if (lastCrash != null && (System.currentTimeMillis() - crashTime < 60000)) {
                // Clear the recorded crash so it does not repeat
                prefs.edit().remove("last_crash").apply()
                AlertDialog.Builder(this)
                    .setTitle("App Recovered from Error")
                    .setMessage(lastCrash)
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to check previous crash", e)
        }
    }

    private fun renderCrashFallback(t: Throwable) {
        val scrollView = ScrollView(this).apply {
            setPadding(32, 64, 32, 32)
        }
        val textView = TextView(this).apply {
            text = "An initialization error occurred:\n\n${t.message}\n\n${Log.getStackTraceString(t)}"
            textSize = 14f
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemState(this)
    }

    override fun onStop() {
        super.onStop()
        // Lock app on backgrounding if biometric lock is active
        viewModel.lockApp()
    }

    private fun promptUnlock() {
        BiometricPromptHelper.showBiometricPrompt(
            activity = this,
            title = "Unlock OTP Forwarder",
            subtitle = "Authenticate using fingerprint or device screen lock",
            onSuccess = {
                viewModel.unlockApp()
            }
        )
    }

    private fun requestSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}
