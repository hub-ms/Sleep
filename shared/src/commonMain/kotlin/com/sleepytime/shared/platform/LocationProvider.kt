package com.sleepytime.shared.platform

data class LatLng(val lat: Double, val lng: Double) {
    companion object {
        val DEFAULT = LatLng(37.5665, 126.9780)
    }
}

expect class LocationProvider {
    suspend fun getCurrentLocation(): LatLng
}