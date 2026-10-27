package com.jarvis.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.jarvis.assistant.ui.JarvisApp

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WAKE = "wake"
    }

    private val vm: JarvisViewModel by viewModels()
    private val wake = mutableStateOf(false)

    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refreshRuntime() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaunchedEffect(wake.value) {
                if (wake.value) {
                    vm.wakeUp()
                    wake.value = false
                }
            }
            JarvisApp(vm)
        }
        requestPermissions()
        if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) wake.value = true
    }

    override fun onResume() {
        super.onResume()
        // Permissions and screen control can change while the app is in the background.
        vm.refreshRuntime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) wake.value = true
    }

    private fun requestPermissions() {
        val list = PhoneActionCatalog.runtimePermissions(Build.VERSION.SDK_INT)
        if (list.isNotEmpty()) perms.launch(list.toTypedArray())
    }
}
