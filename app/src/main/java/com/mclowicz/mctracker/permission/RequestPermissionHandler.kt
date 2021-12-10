package com.mclowicz.mctracker.permission

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.mclowicz.mctracker.features.main.MainActivity
import java.lang.Exception

class RequestPermissionHandler(
    private val context: Fragment,
    private val requestedPermissions: Array<String>,
    private val onGrantedPermissionsCallback: () -> Unit,
    private val onDeniedPermissionCallback: () -> Unit
) : RequestPermissionActions {

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun register() {
        this.permissionLauncher =
            context.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                when (permissions.all { it.value }) {
                    true -> onGrantedPermissionsCallback()
                    else -> onDeniedPermissionCallback()
                }
            }
    }

    override fun request() {
        when {
            requestedPermissions.all { requestedPermission ->
                ContextCompat.checkSelfPermission(
                    this.context.requireContext(),
                    requestedPermission
                ) == PackageManager.PERMISSION_GRANTED
            } -> this.onGrantedPermissionsCallback()
            requestedPermissions.all { requestedPermission ->
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this.context.requireContext() as MainActivity,
                    requestedPermission
                )
            } -> onDeniedPermissionCallback()
            else -> permissionLauncher?.launch(requestedPermissions)
                ?: throw Exception(PERMISSION_EXCEPTION_MESSAGE)
        }
    }

    companion object {
        private const val PERMISSION_EXCEPTION_MESSAGE = "Permission launcher, need to be register before usage!"
    }
}