/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.graphics.Bitmap

data class TimeFrame(
    val time: Long,
    val bitmap: Bitmap? = null,
    val width: Int = 0,
    val height: Int = 0
)
