package com.example.tafakkurai.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual class PrayerLocationManager {

    // CLLocationManager.delegate is a *weak* reference, so the delegate must be held
    // somewhere else or it is deallocated before the first callback ever fires.
    private var manager: CLLocationManager? = null
    private var delegate: CLLocationManagerDelegateProtocol? = null

    actual suspend fun current(): Coordinates? = suspendCancellableCoroutine { cont ->
        val mgr = CLLocationManager()
        // Prayer times only need city-level accuracy — a coarse fix arrives far sooner.
        mgr.desiredAccuracy = kCLLocationAccuracyKilometer

        var resumed = false
        fun finish(coords: Coordinates?) {
            if (resumed) return
            resumed = true
            mgr.stopUpdatingLocation()
            if (cont.isActive) cont.resume(coords)
        }

        val handler = object : NSObject(), CLLocationManagerDelegateProtocol {

            override fun locationManager(
                manager: CLLocationManager,
                didUpdateLocations: List<*>,
            ) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation
                finish(loc?.coordinate?.useContents { Coordinates(latitude, longitude) })
            }

            override fun locationManager(
                manager: CLLocationManager,
                didFailWithError: NSError,
            ) = finish(null)

            /**
             * Fires immediately when the delegate is assigned and again once the user
             * answers the prompt. Requesting a location while still "not determined"
             * is silently dropped by CoreLocation, which is why the request waits here.
             */
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                when (manager.authorizationStatus) {
                    kCLAuthorizationStatusAuthorizedWhenInUse,
                    kCLAuthorizationStatusAuthorizedAlways,
                    -> manager.requestLocation()

                    kCLAuthorizationStatusDenied,
                    kCLAuthorizationStatusRestricted,
                    -> finish(null)

                    else -> manager.requestWhenInUseAuthorization() // not determined → prompt
                }
            }
        }

        manager = mgr
        delegate = handler
        mgr.delegate = handler

        cont.invokeOnCancellation {
            mgr.stopUpdatingLocation()
            mgr.delegate = null
        }
    }
}
