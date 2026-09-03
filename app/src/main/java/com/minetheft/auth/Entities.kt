package com.minetheft.auth

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "touch_events")
data class TouchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: Int, // ACTION_DOWN, ACTION_MOVE, ACTION_UP
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float
)

@Entity(tableName = "accel_events")
data class AccelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

@Entity(tableName = "gyro_events")
data class GyroEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)
