package io.github.warleysr.dechainer.models

import android.graphics.drawable.Drawable

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val isHidden: Boolean = false,
    val isUninstallBlocked: Boolean = false,
    val timeLimit: TimeLimit = TimeLimit.NONE,
    val reopeningSeconds: Int = 0,
    val timeWindows: TimeWindows = TimeWindows.NONE,
    val isSuspended: Boolean = false,
    val hasExplicitContent: Boolean = false
)
