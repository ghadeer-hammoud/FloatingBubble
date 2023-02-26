package com.example.floatingbubble.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

class Helper {

    companion object{

        @SuppressWarnings("Deprecated")
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean{
            val activityManager = context.getSystemService(AppCompatActivity.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getRunningServices(Integer.MAX_VALUE).forEach {
                if(it.service.className == serviceClass.name)
                    return true
            }

            return false
        }
    }
}