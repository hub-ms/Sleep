package com.sleepytime.shared.platform

actual class LocationProvider {
    actual suspend fun getCurrentLocation(): LatLng {
        return LatLng(37.5665, 126.9780)
    }
}