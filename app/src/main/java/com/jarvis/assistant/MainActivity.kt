package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.ui.JarvisApp
import com.jarvis.assistant.ui.JarvisTheme

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WAKE = "wake"
    }

    private val wake = mutableStateOf(false)

    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                val vm: JarvisViewModel = viewModel()
                LaunchedEffect(wake.value) {
                    if (wake.value) {
                        vm.startListening()
                        wake.value = false
                    }
                }
                JarvisApp(vm)
            }
        }
        requestPermissions()
        if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) wake.value = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) wake.value = true
    }

    private fun requestPermissions() {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.launch(list.toTypedArray())
    }
}
