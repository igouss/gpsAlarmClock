package com.elendal.gpsalarmclock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Shown once before requesting ACCESS_BACKGROUND_LOCATION.
 * Play Store policy requires prominent disclosure before background location is requested.
 */
class PrivacyDisclosureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_disclosure)

        findViewById<Button>(R.id.btn_understand).setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
        findViewById<Button>(R.id.btn_decline).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
