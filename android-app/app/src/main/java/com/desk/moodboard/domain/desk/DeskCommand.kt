package com.desk.moodboard.domain.desk

sealed class DeskCommand {
    data object Up : DeskCommand()
    data object Down : DeskCommand()
    data object Stop : DeskCommand()
    data class Memory(val slot: DeskMemorySlot) : DeskCommand()
}
