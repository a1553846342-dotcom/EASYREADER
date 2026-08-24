package me.trishiraj.shadowglow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.sin

@Composable
internal fun rememberGyroParallaxState(sensitivity: Dp): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sensitivityPx = remember(sensitivity) { with(density) { sensitivity.toPx() } }

    val parallaxOffset = remember { mutableStateOf(0f to 0f) }
    val baselineOrientation = remember { mutableStateOf<FloatArray?>(null) }

    DisposableEffect(context, sensitivityPx) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var sensorEventListener: SensorEventListener? = null

        if (rotationSensor != null) {
            sensorEventListener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val currentOrientationAngles = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, currentOrientationAngles)

                        val currentPitch = currentOrientationAngles[1]
                        val currentRoll = currentOrientationAngles[2]

                        if (baselineOrientation.value == null) {
                            baselineOrientation.value = floatArrayOf(currentPitch, currentRoll)
                        } else {
                            val basePitch = baselineOrientation.value!![0]
                            val baseRoll = baselineOrientation.value!![1]

                            val deltaPitch = currentPitch - basePitch
                            val deltaRoll = currentRoll - baseRoll

                            val newOffsetX = -sin(deltaRoll) * sensitivityPx
                            val newOffsetY = sin(deltaPitch) * sensitivityPx
                            parallaxOffset.value = newOffsetX to newOffsetY
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(
                sensorEventListener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            baselineOrientation.value = null
        }

        onDispose {
            sensorEventListener?.let {
                sensorManager.unregisterListener(it)
            }
            parallaxOffset.value = 0f to 0f
            baselineOrientation.value = null
        }
    }
    return parallaxOffset
}
