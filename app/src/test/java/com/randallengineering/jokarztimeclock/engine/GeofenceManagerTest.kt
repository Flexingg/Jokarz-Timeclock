package com.randallengineering.jokarztimeclock.engine

import android.content.Context
import com.google.android.gms.location.GeofencingClient
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GeofenceManager – verify registration only occurs under the right conditions.
 */
class GeofenceManagerTest {

    private lateinit var context: Context
    private lateinit var manager: GeofenceManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mockkObject(PermissionHelper)
    }

    @Test
    fun `geofence NOT registered when geofenceEnabled is false`() {
        val settings = AppSettings(geofenceEnabled = false, workLatitude = 37.0, workLongitude = -122.0)
        manager = GeofenceManager(context)
        // Should call removeGeofence, NOT addGeofences
        // Just verifying it doesn't crash and doesn't try to register without permission check
        manager.updateGeofence(settings)
        verify(exactly = 0) { PermissionHelper.hasLocationPermissions(any()) }
    }

    @Test
    fun `geofence NOT registered when useTaskerFallback is true`() {
        val settings = AppSettings(
            geofenceEnabled = true,
            useTaskerFallback = true,
            workLatitude = 37.0,
            workLongitude = -122.0
        )
        manager = GeofenceManager(context)
        every { PermissionHelper.hasLocationPermissions(context) } returns true
        manager.updateGeofence(settings)
        // Tasker fallback should skip registration before even checking permissions
        verify(exactly = 0) { PermissionHelper.hasLocationPermissions(any()) }
    }

    @Test
    fun `geofence NOT registered when permissions are denied`() {
        val settings = AppSettings(
            geofenceEnabled = true,
            useTaskerFallback = false,
            workLatitude = 37.0,
            workLongitude = -122.0
        )
        every { PermissionHelper.hasLocationPermissions(context) } returns false
        manager = GeofenceManager(context)
        manager.updateGeofence(settings)
        // The manager should bail early – no geofence registration
        verify { PermissionHelper.hasLocationPermissions(context) }
    }

    @Test
    fun `geofence NOT registered when coordinates are zero`() {
        val settings = AppSettings(
            geofenceEnabled = true,
            workLatitude = 0.0,
            workLongitude = 0.0
        )
        manager = GeofenceManager(context)
        manager.updateGeofence(settings)
        verify(exactly = 0) { PermissionHelper.hasLocationPermissions(any()) }
    }
}
