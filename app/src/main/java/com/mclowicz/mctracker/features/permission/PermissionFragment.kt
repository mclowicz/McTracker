package com.mclowicz.mctracker.features.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.mclowicz.mctracker.R
import com.mclowicz.mctracker.databinding.FragmentPermissionBinding
import com.mclowicz.mctracker.permission.RequestPermissionHandler

class PermissionFragment : Fragment(R.layout.fragment_permission) {

    private lateinit var binding: FragmentPermissionBinding
    private lateinit var requestPermissionHandler: RequestPermissionHandler

    override fun onAttach(context: Context) {
        super.onAttach(context)

        requestPermissionHandler = RequestPermissionHandler(
            context = this,
            requestedPermissions = REQUESTED_PERMISSIONS,
            onGrantedPermissionsCallback = { handleGrantedPermission() },
            onDeniedPermissionCallback = { handleNotGrantedPermission() }
        ).apply { register() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentPermissionBinding.bind(view)

        if (arePermissionsGranted()) {
            findNavController().navigate(PermissionFragmentDirections.actionPermissionFragmentToMapsFragment())
        }
        binding.buttonContinue.setOnClickListener {
            requestPermissionHandler.request()
        }
    }

    private fun arePermissionsGranted() : Boolean = activity?.let {
        REQUESTED_PERMISSIONS.all { requestedPermission ->
            ContextCompat.checkSelfPermission(
                it,
                requestedPermission
            ) == PackageManager.PERMISSION_GRANTED
        }
    } ?: false

    private fun handleNotGrantedPermission() {
        showSnackBar(getString(R.string.permission_denied))
    }

    private fun handleGrantedPermission() {
        findNavController().navigate(PermissionFragmentDirections.actionPermissionFragmentToMapsFragment())
    }

    private fun showSnackBar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .show()
    }

    companion object {
        private val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}