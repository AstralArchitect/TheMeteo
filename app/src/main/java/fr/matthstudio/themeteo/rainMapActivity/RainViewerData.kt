/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import java.nio.ByteBuffer

data class TimeFrame(
    val time: Long,
    val buffer: ByteBuffer? = null,
    val width: Int = 0,
    val height: Int = 0
)
