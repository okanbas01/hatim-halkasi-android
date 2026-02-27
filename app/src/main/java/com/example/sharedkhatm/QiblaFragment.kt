package com.example.sharedkhatm

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlin.math.abs
import android.Manifest
import android.content.pm.PackageManager

class QiblaFragment : Fragment(R.layout.fragment_qibla), SensorEventListener {

    private lateinit var imgDial: ImageView
    private lateinit var imgArrow: ImageView
    private lateinit var txtDegree: TextView
    private lateinit var txtStateMessage: TextView
    private lateinit var viewSuccess: View

    private var sensorManager: SensorManager? = null

    private var currentArrowDegree = 0f

    private var qiblaAngle = 0f
    private var hasLocation = false
    private var isSuccessState = false

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val ALPHA = 0.05f

    /** true = TYPE_ROTATION_VECTOR kullanılıyor (daha güvenilir), false = acc + manyetik alan */
    private var useRotationVector = false
    /** Hiç pusula sensörü yok (kullanıcıya mesaj göster) */
    private var hasCompassSensor = false

    private val resolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) getUserLocation()
        else txtDegree.text = "Konum açılmadı."
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgDial = view.findViewById(R.id.imgCompassDial)
        imgArrow = view.findViewById(R.id.imgQiblaArrow)
        txtDegree = view.findViewById(R.id.txtDegree)
        txtStateMessage = view.findViewById(R.id.txtStateMessage)
        viewSuccess = view.findViewById(R.id.viewSuccessOverlay)

        val ctx = context ?: return
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        view.findViewById<View>(R.id.btnBackQibla).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        checkSavedLocation()
    }

    private fun checkSavedLocation() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
        val savedLat = prefs.getFloat("lat", 0f)
        val savedLong = prefs.getFloat("long", 0f)

        if (savedLat != 0f && savedLong != 0f) {
            calculateQibla(savedLat.toDouble(), savedLong.toDouble())
            hasLocation = true
            checkDeviceLocationSettings()
        } else {
            checkDeviceLocationSettings()
        }
    }

    private fun checkDeviceLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { getUserLocation() }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    resolutionLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {}
            }
        }
    }

    private fun getUserLocation() {
        val ctx = context ?: return
        val act = activity ?: return

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            txtDegree.text = "İzin Gerekli"
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(act)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (!isAdded) return@addOnSuccessListener
                if (location != null) {
                    calculateQibla(location.latitude, location.longitude)
                    hasLocation = true
                    val prefs = ctx.getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putFloat("lat", location.latitude.toFloat()).putFloat("long", location.longitude.toFloat()).apply()
                } else {
                    txtDegree.text = "Sinyal bekleniyor..."
                }
            }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!hasLocation || qiblaAngle == 0f) return

        val azimuth = if (useRotationVector && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            getAzimuthFromRotationVector(event.values)
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                gravity = lowPass(event.values.clone(), gravity)
            }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = lowPass(event.values.clone(), geomagnetic)
            }
            if (gravity != null && geomagnetic != null) {
                getAzimuthFromAccMag(gravity!!, geomagnetic!!)
            } else null
        }

        if (azimuth != null) {
            val arrowTarget = qiblaAngle - azimuth
            // ANR önleme: sadece 0.5° üzeri farkta güncelle (aşırı invalidate yok)
            if (angleDiff(arrowTarget, currentArrowDegree) > 0.5f) {
                imgArrow.rotation = arrowTarget
                currentArrowDegree = arrowTarget
            }
            var diff = abs(arrowTarget % 360)
            if (diff > 180) diff = 360 - diff
            // Histerezis: Kıble bulundu 12° içinde girilir, 18° dışına çıkınca kaybolur (titreme azalır)
            val enterSuccessDeg = 12f
            val exitSuccessDeg = 18f
            if (diff < enterSuccessDeg) {
                if (!isSuccessState) onSuccess()
            } else if (diff > exitSuccessDeg && isSuccessState) {
                onNeutral()
            }
        }
    }

    /** TYPE_ROTATION_VECTOR ile azimuth (derece, 0–360). Birçok cihazda daha güvenilir. */
    private fun getAzimuthFromRotationVector(values: FloatArray): Float? {
        if (values.size < 4) return null
        val R = FloatArray(9)
        try {
            SensorManager.getRotationMatrixFromVector(R, values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360
            return azimuth
        } catch (e: Exception) {
            return null
        }
    }

    /** İvmeölçer + manyetometre ile azimuth (derece, 0–360). */
    private fun getAzimuthFromAccMag(gravity: FloatArray, geomagnetic: FloatArray): Float? {
        val R = FloatArray(9)
        val I = FloatArray(9)
        return if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360
            azimuth
        } else null
    }

    /** İki açı arasındaki fark (0–180°). Sensör güncellemesi throttle için kullanılır. */
    private fun angleDiff(a: Float, b: Float): Float {
        var d = abs(a - b)
        while (d > 360f) d -= 360f
        if (d > 180f) d = 360f - d
        return d
    }

    private fun onSuccess() {
        isSuccessState = true
        viewSuccess.visibility = View.VISIBLE

        txtStateMessage.text = "KIBLE BULUNDU 🤲"
        txtStateMessage.setTextColor(Color.parseColor("#2E7D32"))
        txtDegree.setTextColor(Color.parseColor("#2E7D32"))

        val v = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            v?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v?.vibrate(200)
        }
    }

    private fun onNeutral() {
        isSuccessState = false
        viewSuccess.visibility = View.GONE

        txtStateMessage.text = "Ok'u Kabe simgesine hizalayın"
        txtStateMessage.setTextColor(Color.parseColor("#757575"))
        txtDegree.setTextColor(Color.parseColor("#C5A059"))
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + ALPHA * (input[i] - output[i])
        }
        return output
    }

    private fun calculateQibla(lat: Double, lon: Double) {
        val kaabaLat = 21.422487
        val kaabaLong = 39.826206
        val userLoc = Location("u"); userLoc.latitude = lat; userLoc.longitude = lon
        val destLoc = Location("d"); destLoc.latitude = kaabaLat; destLoc.longitude = kaabaLong

        var bearing = userLoc.bearingTo(destLoc)
        if (bearing < 0) bearing += 360

        qiblaAngle = bearing
        txtDegree.text = "Kıble Açısı: ${bearing.toInt()}°"
    }

    override fun onResume() {
        super.onResume()
        hasCompassSensor = false
        useRotationVector = false
        sensorManager?.let { sm ->
            try {
                // Önce TYPE_ROTATION_VECTOR dene (birçok cihazda daha güvenilir; ivme + manyetometre birleşik)
                val rotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                if (rotationVector != null) {
                    sm.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
                    useRotationVector = true
                    hasCompassSensor = true
                } else {
                    // Yoksa klasik ivme + manyetometre (bazı cihazlarda manyetometre yok veya zayıf)
                    val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                    if (acc != null && mag != null) {
                        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
                        sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                        hasCompassSensor = true
                    }
                }
                if (!hasCompassSensor && isAdded) {
                    txtDegree.text = "Pusula bu cihazda kullanılamıyor"
                    txtStateMessage.text = "Bu telefonda pusula sensörü yok veya desteklenmiyor."
                }
            } catch (e: Exception) {
                if (isAdded) txtDegree.text = "Pusula başlatılamadı"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
