package com.swaroopsingh.qrreward.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.swaroopsingh.qrreward.manager.FirebaseManager

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Anonymous login
        FirebaseManager.initUser { uid ->
            // Step 2: Cache settings silently
            FirebaseManager.fetchAndCacheSettings {
                // Step 3: Check daily reset
                FirebaseManager.getUserData(uid) { _, _, lastTs ->
                    FirebaseManager.checkAndResetDailyRewards(uid, lastTs)
                    // Step 4: Go to MainActivity
                    runOnUiThread {
                        startActivity(Intent(this, MainActivity::class.java)
                            .putExtra("uid", uid))
                        finish()
                    }
                }
            }
        }
    }
}
