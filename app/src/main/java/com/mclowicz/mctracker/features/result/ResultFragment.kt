package com.mclowicz.mctracker.features.result

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mclowicz.mctracker.R
import com.mclowicz.mctracker.databinding.FragmentResultBinding

class ResultFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentResultBinding
    private val args: ResultFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentResultBinding.inflate(inflater)
        binding.apply {
            textDistanceValue.text = getString(R.string.result, args.result.distance)
            textDistanceTimeValue.text = args.result.time
            buttonShare.setOnClickListener { shareResult() }
        }
        return binding.root
    }

    private fun shareResult() {
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "I went ${args.result.distance}km in ${args.result.time}!")
        }.also { startActivity(it) }
    }
}