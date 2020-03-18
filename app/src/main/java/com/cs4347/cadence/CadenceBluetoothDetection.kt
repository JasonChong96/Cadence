package com.cs4347.cadence

import android.bluetooth.BluetoothDevice
import android.content.Intent
import com.bluetoothscanning.BluetoothDetection
import com.bluetoothscanning.IDetected
import com.bluetoothscanning.databinding.ActivityMainBinding

class CadenceBluetoothDetection : BluetoothDetection(), IDetected {
    override fun startPulse() {
        try {
            val field =
                BluetoothDetection::class.java.getDeclaredField("mainBinding")
            field.isAccessible = true
            val mainBinding = field[this] as ActivityMainBinding
            mainBinding.pulsator.post {
                mainBinding.pulsator.listener = this@CadenceBluetoothDetection
                mainBinding.pulsator.start()
            }
//            for (i in 1 until mainBinding.backgroundcolor.childCount) {
//                val view = mainBinding.backgroundcolor.getChildAt(i)
//                if (view !is ImageView) return
//                view.setImageDrawable(ScaleDrawable(view.drawable, Gravity.CENTER, 0.5f, 0.5f))
//            }
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }

    override fun onSelectedDevice(device: BluetoothDevice) {
        Intent(this, MainActivity::class.java).also { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.putExtra("DEVICE_NAME", device!!.name!!)
            this.startActivity(intent)
        }
    }
}