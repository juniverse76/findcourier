package xyz.juniverse.findcourier

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

// 퍼미션 체크
fun AppCompatActivity.checkPermissions(requiredPermissions: List<String>, reqCode: Int): Boolean {
    Log.d("juniverse", "checking for permission")
    val arrayOfPermission = ArrayList<String>()
    requiredPermissions.forEach {
        if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
            arrayOfPermission.add(it)
        }
    }

    Log.d("juniverse", "need to ask ${arrayOfPermission.size} permissions")
    if (arrayOfPermission.size > 0) {
        requestPermissions(arrayOfPermission.toTypedArray(), reqCode)
        return false
    }

    return true
}

fun AppCompatActivity.allPermissionsGranted(requiredPermissions: List<String>): Boolean {
    requiredPermissions.forEach {
        if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
    }
    return true
}

fun AppCompatActivity.getVibrator() : Vibrator {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return (getSystemService(AppCompatActivity.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        return getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
    }
}

val Context.isMyLauncherDefault: Boolean
    get() = ArrayList<ComponentName>().apply {
        packageManager.getPreferredActivities(
            arrayListOf(IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }),
            this,
            packageName
        )
    }.isNotEmpty()
