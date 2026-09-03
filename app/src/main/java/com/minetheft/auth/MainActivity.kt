package com.minetheft.auth

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.IO + activityJob)
    
    private val touchBuffer = mutableListOf<TouchEvent>()
    private val BATCH_SIZE = 50

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Simple layout programmatically to avoid xml for now, or we can use xml.
        setContentView(R.layout.activity_main)
        
        db = AppDatabase.getDatabase(this)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyAdminReceiver::class.java)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnAdmin = findViewById<Button>(R.id.btnAdmin)
        val btnLock = findViewById<Button>(R.id.btnLock)
        val statusText = findViewById<TextView>(R.id.statusText)

        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                    return@setOnClickListener
                }
            }
            val serviceIntent = Intent(this, DataCollectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            statusText.text = "Service Running"
        }

        btnStop.setOnClickListener {
            val serviceIntent = Intent(this, DataCollectionService::class.java).apply {
                action = DataCollectionService.ACTION_STOP_SERVICE
            }
            startService(serviceIntent)
            statusText.text = "Service Stopped"
            flushTouches()
        }
        
        btnAdmin.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(compName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "We need this to lock the screen upon anomaly detection.")
                startActivityForResult(intent, 102)
            } else {
                Toast.makeText(this, "Admin already active", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnLock.setOnClickListener {
            if (devicePolicyManager.isAdminActive(compName)) {
                devicePolicyManager.lockNow()
            } else {
                Toast.makeText(this, "Enable Admin First", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            val timestamp = System.currentTimeMillis()
            val action = it.actionMasked
            val x = it.x
            val y = it.y
            val pressure = it.pressure
            val size = it.size
            
            touchBuffer.add(TouchEvent(0, timestamp, action, x, y, pressure, size))
            if (touchBuffer.size >= BATCH_SIZE) {
                val batch = touchBuffer.toList()
                touchBuffer.clear()
                activityScope.launch {
                    db.sensorDao().insertTouchEvents(batch)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        flushTouches()
        activityJob.cancel()
    }
    
    private fun flushTouches() {
        val batch = touchBuffer.toList()
        touchBuffer.clear()
        if (batch.isNotEmpty()) {
            activityScope.launch {
                db.sensorDao().insertTouchEvents(batch)
            }
        }
    }
}
