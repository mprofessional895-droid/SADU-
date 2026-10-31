package com.sadu.assistant

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app must survive its own start-up. A property read by an init block before it is
 * declared takes the whole screen down with it (null backing field), which is exactly what
 * a blank screen after launch looks like.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ViewModelStartupTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun theViewModelStartsAndCanReportItsReadiness() {
        val vm = SaduViewModel(app)

        assertNotNull("the Home note is readable", vm.statusNote)
        assertFalse("nothing is loaded in a fresh test", vm.brainReady)
        assertNotNull(vm.mode)
        assertNotNull(vm.granted)
    }
}
