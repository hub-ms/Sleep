package com.sleepytime.shared.platform
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import kotlin.coroutines.resume

actual class LocationProvider {

    private val manager = CLLocationManager().apply {
        desiredAccuracy = kCLLocationAccuracyBest
        distanceFilter  = kCLDistanceFilterNone
    }

    actual suspend fun getCurrentLocation(): LatLng =
        suspendCancellableCoroutine { cont ->
            val status = CLLocationManager.authorizationStatus()
            val authorized = status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                    status == kCLAuthorizationStatusAuthorizedAlways

            if (!authorized) {
                cont.resume(LatLng.DEFAULT)
                return@suspendCancellableCoroutine
            }

            val loc = manager.location
            if (loc != null) {
                cont.resume(
                    LatLng(
                        loc.coordinate.useContents { latitude },
                        loc.coordinate.useContents { longitude })
                )
            } else {
                cont.resume(LatLng.DEFAULT)
            }
        }
}