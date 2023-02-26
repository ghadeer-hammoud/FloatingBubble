package com.example.floatingbubble

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingbubble.utils.Helper
import com.google.android.material.button.MaterialButton

class HomeActivity: AppCompatActivity() {

    companion object{
        private const val TAG = "HomeActivity"
    }

    private lateinit var btnShowWidget: MaterialButton
    private lateinit var btnStop: MaterialButton

    private lateinit var showEnablePermissionScreen: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnShowWidget = findViewById(R.id.btnShowWidget)
        btnStop = findViewById(R.id.btnStop)


        btnShowWidget.setOnClickListener {

            when (isOverlyPermissionGranted()) {
                true -> {
                    startService()
                }
                false -> {
                    requestOverlyPermission()
                }
            }
        }

        btnStop.setOnClickListener { stopService() }


        showEnablePermissionScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startService()

            }
        }
    }

    private fun isOverlyPermissionGranted(): Boolean{

        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            return Settings.canDrawOverlays(this)
        }
        return true
    }

    private fun requestOverlyPermission(){
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        showEnablePermissionScreen.launch(intent)
    }

    private fun startService(){

        if(!Helper.isServiceRunning(this, FloatingWidgetService::class.java)){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(Intent(this, FloatingWidgetService::class.java))
            else
                startService(Intent(this, FloatingWidgetService::class.java))
            //finish()
        }
    }

    private fun stopService(){
        stopService(Intent(this, FloatingWidgetService::class.java))
    }
}