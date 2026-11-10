package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.VaultDatabase
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultDatabaseSecurityTest {
    @Test fun missingNativeCipherCannotCreatePlaintextDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "must-not-be-plaintext.db")
        assertThrows(IllegalStateException::class.java) {
            VaultDatabase.openSpaceDatabase(context, file, ByteArray(32))
        }
        assertFalse(file.exists())
    }
}
