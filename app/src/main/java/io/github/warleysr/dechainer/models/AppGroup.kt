package io.github.warleysr.dechainer.models

data class AppGroup(
    val id: String,
    val name: String,
    val packageNames: Set<String> = emptySet(),
    val timeLimit: TimeLimit = TimeLimit.NONE,
    val timeWindows: TimeWindows = TimeWindows.NONE
)
