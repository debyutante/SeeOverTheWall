package com.debyhutante.seeoverthewall

import android.content.Context
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.cos
import kotlin.math.pow

class SignalCollector(private val context: Context) {

    private val points = mutableListOf<MainActivity.SignalPoint>()

    private var routerLocation: Pair<Float, Float> = Pair(Float.NaN, Float.NaN)

    fun onTriangButtonClick(location: Location, azimuth: Float, currentLoca: String): Pair<Float, Float> {
        /*if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Toast.makeText(this@MainActivity, "GPS is not enabled", Toast.LENGTH_SHORT).show()
                    return
                }*/
        Log.d("22222 onTriangButtonClick", "onTriangButtonClick")

        val estimDi = estimateDistance(context)
        Log.d("22222 estimDi", "$estimDi")

        val strength = getSignalStrength(context)

        val newPoint = MainActivity.SignalPoint(location, strength, azimuth, estimDi)

        Log.d("22222 newPoint.location", newPoint.location.toString())

        points.add(newPoint)

        Log.d("22222 points.size", points.size.toString())

        if (points.size >= 3) {
            Log.d("22222 égale 3", "$points")
            routerLocation = estimateRouterDirectionWithAzi(points, currentLoca)
            println("22222 estimateRouterDirectionWithAzi : $routerLocation")
            //points.clear()
        }

        return routerLocation

    }

    fun removeCoor(ind: Int) {
        if (ind == 1) {
            if (points.isNotEmpty()) {
                points.removeAt(0)
            }
        } else if (ind == 2) {
            if (points.size > 1) {
                points.removeAt(1)
            }
        } else if (ind == 3) {
            if (points.size > 2) {
                points.removeAt(2)
            }
        }
    }

    fun getSignalStrength(context: Context): Int {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        if (info.bssid != null) {
            return WifiManager.calculateSignalLevel(info.rssi, 100)
        }
        return 0
    }

    fun estimateDistance(context: Context): Double {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        if (info.bssid != null) {
            val txPower = -30 // This is a constant and might need to be adjusted depending on the actual transmit power of your Wi-Fi router (in dBm)
            val rssi = info.rssi
            val n = 2.0 // This is a constant representing the signal propagation constant. The value 2 is for free space and might need to be adjusted depending on the environment.

            val distance = 10.0.pow((txPower - rssi) / (10 * n))
            Log.d("22222 estimateDistance", "$distance")

            return distance
        }
        return 0.0
    }


    /*private fun estimateRouterDirection(points: List<MainActivity.SignalPoint>, context: Context, currentLoca: String): Location {

        val loc1 = points[0].location
        val loc2 = points[1].location
        val loc3 = points[2].location

        val strength1 = points[0].strength
        val strength2 = points[1].strength
        val strength3 = points[2].strength

        val azimuth1 = points[0].azimuth
        val azimuth2 = points[1].azimuth
        val azimuth3 = points[2].azimuth

        val estimDi1 = points[0].estimDist
        val estimDi2 = points[1].estimDist
        val estimDi3 = points[2].estimDist

        return
    }*/

    private fun estimateRouterDirectionWithAzi(
        points: List<MainActivity.SignalPoint>,
        currentLoca: String
    ): Pair<Float, Float> {
        val weights = points.map { cos(it.azimuth) }
        val totalWeight = weights.sum()

        val weightedLocations = points.zip(weights).map { (point, weight) ->
            Location("weighted").also {
                it.latitude = point.location.latitude * weight / totalWeight
                it.longitude = point.location.longitude * weight / totalWeight
            }
        }

        val routerLocation = weightedLocations.reduce { acc, location ->
            acc.apply {
                latitude += location.latitude
                longitude += location.longitude
            }
        }

        val currentLocationParts = currentLoca.split(",")
        if (currentLocationParts.size != 2) {
            throw IllegalArgumentException("currentLoca doit être au format 'latitude,longitude'")
        }

        val currentLocation = Location("current").also {
            it.latitude = currentLocationParts[0].toDouble()
            it.longitude = currentLocationParts[1].toDouble()
        }

        val distance = currentLocation.distanceTo(routerLocation)
        val azimuth = currentLocation.bearingTo(routerLocation)

        val logTag = "EstimateRouterDirection"
        Log.d(logTag, "22222 Distance: $distance")
        Log.d(logTag, "22222 Azimuth: $azimuth")

        return Pair(distance, azimuth)
    }




    private fun trilaterate(p1: Location, p2: Location, p3: Location, r1: Double, r2: Double, r3: Double): Location {
        val x1 = p1.longitude
        val y1 = p1.latitude
        val x2 = p2.longitude
        val y2 = p2.latitude
        val x3 = p3.longitude
        val y3 = p3.latitude

        val A = 2*x2 - 2*x1
        val B = 2*y2 - 2*y1
        val C = r1*r1 - r2*r2 - x1*x1 + x2*x2 - y1*y1 + y2*y2
        val D = 2*x3 - 2*x2
        val E = 2*y3 - 2*y2
        val F = r2*r2 - r3*r3 - x2*x2 + x3*x3 - y2*y2 + y3*y3

        val x = (C*E - F*B) / (E*A - B*D)
        val y = (C*D - A*F) / (B*D - A*E)

        val estimatedLocation = Location("estimated")
        estimatedLocation.longitude = x
        estimatedLocation.latitude = y

        return estimatedLocation
    }

}

