package com.cs4347.cadence

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Semaphore


class MainActivity : AppCompatActivity() {
    var isStarted = false
    var toggleSemaphore = Semaphore(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            startService(intent)
        }
        val button = findViewById<Button>(R.id.button2)
        isStarted = true
        button.text = "Stop"
    }

    fun toggleCounting(view: View) {
        toggleSemaphore.acquire()
        if (isStarted) {
            stopCounting()
        } else {
            startCounting()
        }
        toggleSemaphore.release()
    }

    private fun startCounting() {
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            startService(intent)
        }
        val button = findViewById<Button>(R.id.button2)
        isStarted = true
        button.text = "Stop"
    }

    private fun stopCounting() {
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            stopService(intent)
        }
        val button = findViewById<Button>(R.id.button2)
        isStarted = false
        button.text = "Start"
    }
}
