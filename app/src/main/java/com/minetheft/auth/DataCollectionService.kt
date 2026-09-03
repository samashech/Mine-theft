package com.minetheft.auth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DataCollectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var db: AppDatabase
    
    // Batching to avoid excessive DB writes
    private val accelBuffer = mutableListOf<AccelEvent>()
    private val gyroBuffer = mutableListOf<GyroEvent>()
    private val BATCH_SIZE = 100

    companion object {
        const val CHANNEL_ID = "DataCollectionChannel"
        const val NOTIFICATION_ID = 1
        
        // Action to stop service
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Biometric Auth")
            .setContentText("Collecting sensor data...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
        
        // 50Hz = 20,000 microseconds
        accelSensor?.let { sensorManager.registerListener(this, it, 20000) }
        gyroSensor?.let { sensorManager.registerListener(this, it, 20000) }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
        flushBuffers()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val timestamp = System.currentTimeMillis() // Aligning to currentTimeMillis for easier sync with touch
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelBuffer.add(AccelEvent(0, timestamp, event.values[0], event.values[1], event.values[2]))
                if (accelBuffer.size >= BATCH_SIZE) {
                    val batch = accelBuffer.toList()
                    accelBuffer.clear()
                    serviceScope.launch {
                        db.sensorDao().insertAccelEvents(batch)
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroBuffer.add(GyroEvent(0, timestamp, event.values[0], event.values[1], event.values[2]))
                if (gyroBuffer.size >= BATCH_SIZE) {
                    val batch = gyroBuffer.toList()
                    gyroBuffer.clear()
                    serviceScope.launch {
                        db.sensorDao().insertGyroEvents(batch)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun flushBuffers() {
        val aBatch = accelBuffer.toList()
        val gBatch = gyroBuffer.toList()
        accelBuffer.clear()
        gyroBuffer.clear()
        serviceScope.launch {
            if (aBatch.isNotEmpty()) db.sensorDao().insertAccelEvents(aBatch)
            if (gBatch.isNotEmpty()) db.sensorDao().insertGyroEvents(gBatch)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Collection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
