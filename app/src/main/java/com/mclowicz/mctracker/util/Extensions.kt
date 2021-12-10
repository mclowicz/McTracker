package com.mclowicz.mctracker.util

import android.view.View
import android.widget.Button

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.INVISIBLE
}

fun Button.enable() {
    isEnabled = true
}

fun Button.disable() {
    isEnabled = false
}