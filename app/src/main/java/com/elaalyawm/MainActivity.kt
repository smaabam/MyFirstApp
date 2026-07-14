package com.elaalyawm

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.elaalyawm.ui.DriveAction
import com.elaalyawm.ui.ElaApp
import com.elaalyawm.ui.ElaViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class MainActivity : ComponentActivity() {
    private val viewModel: ElaViewModel by viewModels()
    private var pendingDriveAction: DriveAction? by mutableStateOf(null)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.reloadPrayerTimes()
    }
    private val driveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = pendingDriveAction ?: return@registerForActivityResult
        pendingDriveAction = null
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).result.account ?: error("لا يوجد حساب Google") }
            .onSuccess { account -> if (action == DriveAction.UPLOAD) viewModel.uploadBackup(account) else viewModel.restoreBackup(account) }
            .onFailure { viewModel.reportMessage("تعذر تسجيل الدخول إلى Drive") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElaApp(
                viewModel = viewModel,
                requestPermissions = ::requestAppPermissions,
                requestExactAlarms = ::requestExactAlarmPermission,
                requestDrive = ::requestDrive
            )
        }
    }

    private fun requestAppPermissions() {
        val permissions = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray()) else viewModel.reloadPrayerTimes()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }

    private fun requestDrive(action: DriveAction) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(Scope(DriveScopes.DRIVE_APPDATA)).build()
        val client = GoogleSignIn.getClient(this, options)
        val existing = GoogleSignIn.getLastSignedInAccount(this)
        pendingDriveAction = action
        if (existing != null && existing.account != null && GoogleSignIn.hasPermissions(existing, Scope(DriveScopes.DRIVE_APPDATA))) {
            pendingDriveAction = null
            if (action == DriveAction.UPLOAD) viewModel.uploadBackup(existing.account!!) else viewModel.restoreBackup(existing.account!!)
        } else driveLauncher.launch(client.signInIntent)
    }
}
