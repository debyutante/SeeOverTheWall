package com.debyhutante.seeoverthewall


import android.os.Bundle
import androidx.activity.ComponentActivity
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.location.Location
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ncorti.slidetoact.SlideToActView

class MainActivity : ComponentActivity() {

    data class SignalPoint(val location: Location, val strength: Int, val azimuth: Float, val estimDist: Double)

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mediaPlayerAnalyze: MediaPlayer
    private lateinit var mediaPlayerSonar: MediaPlayer
    private var signalStrengthAll:Int = 0
    private var averageStrengthStart:Double = 0.0
    private var differenceStrengthStart:Int = 999
    private lateinit var compassView: CompassView
    private lateinit var handlerPrincipal: Handler
    private lateinit var runnableCode: Runnable
    private var taille:Float = 0.0f

    private var nbrReload:Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val slideToActView = findViewById<SlideToActView>(R.id.start)

        val displayMetrics = resources.displayMetrics
        val widtha = displayMetrics.widthPixels / displayMetrics.density

        if  (widtha < 321) {
            val textTitle = findViewById<TextView>(R.id.title)
            val textSize = 40
            textTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
            val intro0 = findViewById<TextView>(R.id.intro0)
            val textSizeintro0 = 23
            intro0.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeintro0.toFloat())
            val intro = findViewById<TextView>(R.id.intro)
            val textSizeintro = 20
            intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeintro.toFloat())

            val buttonReload: Button = findViewById(R.id.reload)
            val layoutParams = buttonReload.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.height = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                40f,
                resources.displayMetrics
            ).toInt()
            layoutParams.bottomMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                15f,
                resources.displayMetrics
            ).toInt()
            buttonReload.layoutParams = layoutParams
            layoutParams.topMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                0f,
                resources.displayMetrics
            ).toInt()
            buttonReload.layoutParams = layoutParams
            val layoutParams2 = slideToActView.layoutParams
            layoutParams2.height = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                50f,
                resources.displayMetrics
            ).toInt()
            slideToActView.layoutParams = layoutParams2

        }

        mediaPlayer = MediaPlayer.create(this, R.raw.sonar)
        mediaPlayerAnalyze = MediaPlayer.create(this, R.raw.analyze)
        mediaPlayerSonar = MediaPlayer.create(this, R.raw.sonarout)
        compassView = CompassView(this@MainActivity)

        slideToActView.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (!wifiManager.isWifiEnabled) {
                    Toast.makeText(this@MainActivity, getString(R.string.wifi), Toast.LENGTH_SHORT).show()
                    slideToActView.resetSlider()
                    return
                }

                val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = connectivityManager.activeNetworkInfo
                if (networkInfo == null || networkInfo.type != ConnectivityManager.TYPE_WIFI) {
                    Toast.makeText(this@MainActivity, getString(R.string.wifi2), Toast.LENGTH_SHORT).show()
                    slideToActView.resetSlider()
                    return
                }
                start()
            }
        }

        handlerPrincipal = Handler(Looper.getMainLooper())
        val signalStrengthList = mutableListOf<Int>()
        var counter = 0

        runnableCode = object : Runnable {
            override fun run() {

                handlerPrincipal.postDelayed(this, 1000)

                if (averageStrengthStart != 0.0 && differenceStrengthStart != 999) {

                    signalStrengthAll = getSignalStrength(this@MainActivity)
                    //Log.d("111111 signalStrengthAll", "$signalStrengthAll")

                    signalStrengthList.add(signalStrengthAll)
                    counter++

                    if (counter == 3) {

                        val averageSignalStrength = signalStrengthList.average()
                        Log.d("111111 averageSignalStrength", "$averageSignalStrength")
                        Log.d("111111 averageStrengthStart", "$averageStrengthStart")

                        val maxSignalStrength = signalStrengthList.maxOrNull() ?: Double.MIN_VALUE
                        val minSignalStrength = signalStrengthList.minOrNull() ?: Double.MAX_VALUE
                        val differenceSignalStrength = maxSignalStrength.toInt() - minSignalStrength.toInt()

                        val cours = signalStrengthList.first() - signalStrengthList.last()

                        val calc = averageStrengthStart - 2
                        Log.d("111111 calc", "$calc")

                        //Log.d("111111 differenceSignalStrength", "$differenceSignalStrength")

                        val limitChange = averageStrengthStart + 10

                        if (averageSignalStrength > limitChange || differenceStrengthStart > 5) {
                            nbrReload++
                            reload(null, true)
                            if (nbrReload < 3) {
                                Toast.makeText(this@MainActivity, getString(R.string.recal), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, getString(R.string.recal2), Toast.LENGTH_LONG).show()
                            }
                            counter = 0
                            signalStrengthList.clear()
                            handlerPrincipal.removeCallbacks(runnableCode)
                        } else if (averageSignalStrength < calc || differenceSignalStrength > 10) {
                            nbrReload = 0
                            Log.d("111111 DETECT", "CHOSE DETECTER")

                            if (mediaPlayerSonar.isPlaying) {
                                mediaPlayerSonar.pause()
                            }
                            if (!mediaPlayer.isPlaying) {
                                mediaPlayer.isLooping = true
                                mediaPlayer.start()
                            }

                            val ecartPuissance = calc - averageSignalStrength

                            if (differenceSignalStrength > 20 || ecartPuissance > 10) {
                                taille = 1.2F
                            } else if (differenceSignalStrength in 11..19 || ecartPuissance > 4 && ecartPuissance < 11) {
                                taille = 2.0F
                            } else if (ecartPuissance < 5) {
                                taille = 3.0F
                            }

                            if (cours in -5..-3) {
                                if (taille != 1.2F) {
                                    taille -= 1.0F
                                }
                            } else if (cours in -2..0) {
                                if (taille != 1.2F) {
                                    taille -= 0.5F
                                }
                            } else if (cours in 1..2) {
                                taille += 0.5F
                            } else if (cours in 3..5) {
                                taille += 1.0F
                            }

                            Log.d("111111 taille", "$taille")
                            compassView.showGreenBlur(taille)

                        } else {
                            nbrReload = 0
                            if (mediaPlayer.isPlaying) {
                                mediaPlayer.pause()
                            }
                            if (!mediaPlayerSonar.isPlaying) {
                                mediaPlayerSonar.isLooping = true
                                mediaPlayerSonar.start()
                            }
                            compassView.hideGreenBlur()

                            averageStrengthStart = (averageSignalStrength + averageStrengthStart)/2
                        }

                        counter = 0
                        signalStrengthList.clear()
                    }
                }
            }
        }

        handlerPrincipal.post(runnableCode)

    }

    fun start() {

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this@MainActivity, getString(R.string.wifi), Toast.LENGTH_SHORT).show()
            return
        }

        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo == null || networkInfo.type != ConnectivityManager.TYPE_WIFI) {
            Toast.makeText(this@MainActivity, getString(R.string.wifi2), Toast.LENGTH_SHORT).show()
            return
        }

        val contentLoader = findViewById<LinearLayout>(R.id.contentLoader)
        contentLoader.visibility = View.VISIBLE

        calculateSignalMetrics(this)

    }


    fun calculateSignalMetrics(context: Context) {

        mediaPlayerAnalyze.isLooping = true
        mediaPlayerAnalyze.start()

        val contentLoader = findViewById<LinearLayout>(R.id.contentLoader)
        val counter = findViewById<TextView>(R.id.counter)
        var count = 12
        counter.text = "12"

        val handler = Handler(Looper.getMainLooper())
        val signalStrengths = mutableListOf<Int>()
        val runnableCode: Runnable = object : Runnable {
            override fun run() {
                val signalStrength = getSignalStrength(context)
                signalStrengths.add(signalStrength)
                Log.d(" 111111 WIFI_STRENGTH", "$signalStrength")

                if (signalStrengths.size < 12) {
                    count--
                    counter.text = ""
                    counter.text = "$count"
                    handler.postDelayed(this, 1000)
                } else {
                    val max = signalStrengths.maxOrNull()
                    val min = signalStrengths.minOrNull()

                    if (max != null && min != null) {
                        signalStrengths.remove(max)
                        signalStrengths.remove(min)
                    }

                    val average = signalStrengths.average()
                    val newMax = signalStrengths.maxOrNull() ?: 0
                    val newMin = signalStrengths.minOrNull() ?: 0
                    val difference = newMax - newMin

                    averageStrengthStart = average
                    differenceStrengthStart = difference

                    Log.d("WIFI_STRENGTH", "Average: $average")
                    Log.d("WIFI_STRENGTH", "Difference: $difference")

                    contentLoader.visibility = View.GONE

                    val sonarContent = findViewById<LinearLayout>(R.id.sonarContent)
                    sonarContent.visibility = View.VISIBLE
                    val sonarLayout = findViewById<LinearLayout>(R.id.sonar)
                    sonarLayout.addView(compassView)

                    mediaPlayerAnalyze.pause()
                }
            }
        }

        handler.post(runnableCode)
    }

    fun getSignalStrength(context: Context): Int {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        if (info.bssid != null) {
            return WifiManager.calculateSignalLevel(info.rssi, 100)
        }
        return 0
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        mediaPlayerAnalyze.release()
        mediaPlayerSonar.release()
    }


    fun reload(view: View?, nope:Boolean = false) {

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this@MainActivity, getString(R.string.wifi), Toast.LENGTH_SHORT).show()
            return
        }

        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo == null || networkInfo.type != ConnectivityManager.TYPE_WIFI) {
            Toast.makeText(this@MainActivity, getString(R.string.wifi2), Toast.LENGTH_SHORT).show()
            return
        }

        mediaPlayer.pause()
        mediaPlayerSonar.pause()

        if (!nope) {
            handlerPrincipal.removeCallbacks(runnableCode)
        }

        val sonarLayout = findViewById<LinearLayout>(R.id.sonar)
        sonarLayout.removeAllViews()

        val contentLoader = findViewById<LinearLayout>(R.id.contentLoader)

        contentLoader.visibility = View.VISIBLE

        handlerPrincipal.postDelayed(runnableCode, 12000)
        calculateSignalMetrics(this)

        //handlerPrincipal.post(runnableCode)

    }

}
