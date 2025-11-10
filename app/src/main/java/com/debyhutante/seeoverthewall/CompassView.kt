package com.debyhutante.seeoverthewall


import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.graphics.Color
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.atan2
import kotlin.math.hypot


@SuppressLint("ClickableViewAccessibility")
class CompassView(context: Context) : View(context), SensorEventListener {
    private val paint = Paint()
    private var azimuth: Float = 0f
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastUpdateTime: Long = 0
    private var needleRotation: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var touchAngle: Float? = null
    private var blurPointX: Float = 0f
    private var blurPointY: Float = 0f
    private var showBlur: Boolean = false
    private var globalCenterX: Float = 0f
    private var globalCenterY: Float = 0f
    private var ifRouteur:Boolean = false
    private var clickedAzimuth:Float = 0.0F
    private var trailBitmap: Bitmap? = null
    private var trailCanvas: Canvas? = null
    private val trailPaint = Paint()
    private var tail:Float = 1.0F


    init {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)

        animator.duration = 3000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener { animation ->
            needleRotation = animation.animatedValue as Float
            invalidate()

        }
        animator.start()

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val centerX = width / 2f
                val centerY = height / 2f
                val dx = event.x - centerX
                val dy = event.y - centerY
                touchAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()

                // Calculer l'azimuth du point cliqué
                clickedAzimuth = (Math.toDegrees(touchAngle!!.toDouble()).toFloat() + azimuth + 360) % 360
                Log.d("1111111 Azimuth", "Azimuth du point cliqué: $clickedAzimuth")

                invalidate()
                true
            } else {
                false
            }
        }

    }

    override fun onDraw(canvas: Canvas) {
        globalCenterX = width / 2f
        globalCenterY = height / 2f

        val radius = Math.min(globalCenterX, globalCenterY)
        paint.style = Paint.Style.STROKE
        paint.color = Color.GREEN

        // Créer un bitmap pour dessiner le canvas
        if (trailBitmap == null) {
            trailBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            trailCanvas = Canvas(trailBitmap!!)
        }

        if (ifRouteur) {
            // Effacer légèrement le bitmap pour créer l'effet de traînée
            trailPaint.color = Color.argb(50, 0, 0, 0)
            trailCanvas!!.drawRect(0f, 0f, width.toFloat(), height.toFloat(), trailPaint)

            // Rotation spécifique à l'aiguille
            trailCanvas!!.save()
            trailCanvas!!.rotate(-needleRotation, globalCenterX, globalCenterY)
            paint.strokeWidth = 5f
            paint.color = Color.GREEN
            trailCanvas!!.drawLine(globalCenterX, globalCenterY, globalCenterX, globalCenterY - radius, paint)
            trailCanvas!!.restore()

            // Dessiner le bitmap avec la traînée sur le canvas principal
            canvas.drawBitmap(trailBitmap!!, 0f, 0f, null)
        }

        // Dessiner le cercle et les lettres sur le canvas principal
        canvas.save()
        canvas.rotate(-azimuth, globalCenterX, globalCenterY)
        canvas.drawCircle(globalCenterX, globalCenterY, radius, paint)


        val displayMetrics = resources.displayMetrics
        val widtha = displayMetrics.widthPixels / displayMetrics.density

        if  (widtha < 321) {
            paint.textSize = 30f
        } else {
            paint.textSize = 50f
        }

        val west = context.getString(R.string.west)

        canvas.drawText("N", globalCenterX, globalCenterY - radius + 50, paint)
        canvas.drawText("S", globalCenterX, globalCenterY + radius - 10, paint)
        canvas.drawText("E", globalCenterX + radius - 50, globalCenterY, paint)
        canvas.drawText(west, globalCenterX - radius + 10, globalCenterY, paint)
        canvas.restore()

        // Point routeur
        touchAngle?.let { angle ->
            val pointX = globalCenterX + radius * Math.cos(angle.toDouble()).toFloat()
            val pointY = globalCenterY + radius * Math.sin(angle.toDouble()).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Color.RED
            canvas.drawCircle(pointX, pointY, 10f, paint)
            blurPointX = pointX
            blurPointY = pointY
            ifRouteur = true
        }

        // Ajouter le texte en blanc au milieu du canvas
        if (!ifRouteur) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            if  (widtha < 321) {
                paint.textSize = 35f
            } else {
                paint.textSize = 70f
            }

            val text = context.getString(R.string.clickbou)
            val lines = text.split("\n")
            val lineHeight = paint.descent() - paint.ascent()
            val textHeight = lineHeight * lines.size

            // Centrage vertical et calcul de la position initiale de y
            var yPosition = globalCenterY - textHeight / 2 - paint.ascent()

            for (line in lines) {
                // Centrage horizontal
                val textWidth = paint.measureText(line)
                val xPosition = globalCenterX - textWidth / 2
                canvas.drawText(line, xPosition, yPosition, paint)
                yPosition += lineHeight
            }
        }

        if (showBlur && ifRouteur) {
            drawGreenBlur(canvas, globalCenterX, globalCenterY, blurPointX, blurPointY, tail)
        }
    }



    fun getAzimCli(): Float {
        return clickedAzimuth
    }

    private fun drawGreenBlur(canvas: Canvas, centerX: Float, centerY: Float, pointX: Float, pointY: Float, tail:Float) {
        // Calculer les coordonnées du point médian
        val midX = (centerX + pointX) / 2
        val midY = (centerY + pointY) / 2

        val gradientPaint = Paint()
        val radius = ((hypot((pointX - centerX).toDouble(), (pointY - centerY).toDouble())) / tail).toFloat()
        val gradient = RadialGradient(midX, midY, radius, Color.GREEN, Color.argb(0, 0, 255, 0), Shader.TileMode.CLAMP)
        gradientPaint.shader = gradient
        gradientPaint.style = Paint.Style.FILL

        canvas.drawCircle(midX, midY, radius, gradientPaint)
    }

    fun showGreenBlur(taille:Float) {
        showBlur = true
        tail = taille
        invalidate()
    }

    fun hideGreenBlur() {
        showBlur = false
        invalidate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (currentTime - lastUpdateTime > 250) {

            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading, 0, event.values.size)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, event.values.size)
            }

            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            invalidate()
            lastUpdateTime = currentTime
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

}

