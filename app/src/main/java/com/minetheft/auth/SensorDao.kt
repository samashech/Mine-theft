package com.minetheft.auth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDao {
    @Insert
    suspend fun insertTouchEvent(event: TouchEvent)

    @Insert
    suspend fun insertAccelEvent(event: AccelEvent)

    @Insert
    suspend fun insertGyroEvent(event: GyroEvent)
    
    @Insert
    fun insertAccelEvents(events: List<AccelEvent>)

    @Insert
    fun insertGyroEvents(events: List<GyroEvent>)
    
    @Insert
    fun insertTouchEvents(events: List<TouchEvent>)

    @Query("SELECT * FROM touch_events ORDER BY timestamp ASC")
    suspend fun getAllTouchEvents(): List<TouchEvent>

    @Query("SELECT * FROM accel_events ORDER BY timestamp ASC")
    suspend fun getAllAccelEvents(): List<AccelEvent>

    @Query("SELECT * FROM gyro_events ORDER BY timestamp ASC")
    suspend fun getAllGyroEvents(): List<GyroEvent>
    
    @Query("DELETE FROM touch_events")
    suspend fun clearTouchEvents()
    
    @Query("DELETE FROM accel_events")
    suspend fun clearAccelEvents()
    
    @Query("DELETE FROM gyro_events")
    suspend fun clearGyroEvents()
}
