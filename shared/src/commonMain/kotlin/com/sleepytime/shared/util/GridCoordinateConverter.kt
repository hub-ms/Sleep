package com.sleepytime.shared.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

class GridCoordinateConverter {
    fun convert(lat: Double, lng: Double): Pair<String, String> {
        val re    = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon  = OLON  * DEGRAD
        val olat  = OLAT  * DEGRAD

        val sn = ln(cos(slat1) / cos(slat2)) /
                ln(tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5))
        val sf = tan(PI * 0.25 + slat1 * 0.5).pow(sn) * cos(slat1) / sn
        val ro = re * sf / tan(PI * 0.25 + olat * 0.5).pow(sn)
        val ra = re * sf / tan(PI * 0.25 + lat * DEGRAD * 0.5).pow(sn)

        var theta = lng * DEGRAD - olon
        if (theta >  PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = (ra * sin(theta) + XO + 0.5).toInt()
        val ny = (ro - ra * cos(theta) + YO + 0.5).toInt()
        return nx.toString() to ny.toString()
    }

    companion object {
        private const val RE     = 6371.00877
        private const val GRID   = 5.0
        private const val SLAT1  = 30.0
        private const val SLAT2  = 60.0
        private const val OLON   = 126.0
        private const val OLAT   = 38.0
        private const val XO     = 43.0
        private const val YO     = 136.0
        private const val DEGRAD = PI / 180.0
    }
}