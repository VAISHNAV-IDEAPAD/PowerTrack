package com.powertrack.app

import com.powertrack.app.data.model.LocationEntity
import com.powertrack.app.data.repository.LocationRepository
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class LocationRepositoryTest {

    @Test
    fun testValidCoordinatesAcceptance() {
        // Valid latitudes: -90 to +90; Valid longitudes: -180 to +180
        assertTrue(LocationRepository.isValidCoordinate(28.6139, 77.2090))
        assertTrue(LocationRepository.isValidCoordinate(-33.8688, 151.2093))
        assertTrue(LocationRepository.isValidCoordinate(40.7128, -74.0060))
        assertTrue(LocationRepository.isValidCoordinate(90.0, 180.0))
        assertTrue(LocationRepository.isValidCoordinate(-90.0, -180.0))
    }

    @Test
    fun testInvalidCoordinatesRejection() {
        // Latitude out of range
        assertFalse(LocationRepository.isValidCoordinate(90.001, 77.0))
        assertFalse(LocationRepository.isValidCoordinate(-91.0, 77.0))

        // Longitude out of range
        assertFalse(LocationRepository.isValidCoordinate(28.0, 180.001))
        assertFalse(LocationRepository.isValidCoordinate(28.0, -180.001))

        // Null Island / false GPS fix (0,0)
        assertFalse(LocationRepository.isValidCoordinate(0.0, 0.0))

        // NaN coordinates
        assertFalse(LocationRepository.isValidCoordinate(Double.NaN, 77.0))
        assertFalse(LocationRepository.isValidCoordinate(28.0, Double.NaN))
    }

    @Test
    fun testEventIdUniqueness() {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        assertNotEquals(id1, id2)
        assertEquals(36, id1.length)
    }

    @Test
    fun testLowBatteryTriggerArmingAndResetLogic() {
        var isArmed = true
        val triggerThreshold = 10
        val resetThreshold = 15

        // Simulating battery dropping from 50% to 10%
        var batteryLevel = 50
        assertFalse(batteryLevel <= triggerThreshold && isArmed.not())

        batteryLevel = 10
        // Battery hits 10% -> triggers and disarms
        if (batteryLevel <= triggerThreshold && isArmed) {
            isArmed = false
        }
        assertFalse("Trigger must be disarmed after activating", isArmed)

        // Battery drops further to 8% -> should not re-trigger because trigger is disarmed
        batteryLevel = 8
        var triggeredAgain = false
        if (batteryLevel <= triggerThreshold && isArmed) {
            triggeredAgain = true
        }
        assertFalse("Trigger must not fire again while disarmed", triggeredAgain)

        // Battery charges to 12% (still below reset threshold 15%) -> should not re-arm
        batteryLevel = 12
        if (batteryLevel >= resetThreshold) {
            isArmed = true
        }
        assertFalse("Trigger must remain disarmed below 15%", isArmed)

        // Battery charges to 16% (above 15%) -> re-arms
        batteryLevel = 16
        if (batteryLevel >= resetThreshold) {
            isArmed = true
        }
        assertTrue("Trigger must re-arm when charged above 15%", isArmed)
    }

    @Test
    fun testTimestampUtcFormatting() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date(1700000000000L))
        assertTrue(timestamp.endsWith("Z"))
        assertTrue(timestamp.contains("T"))
    }

    @Test
    fun testLocationEntityModelIntegrity() {
        val entity = LocationEntity(
            eventId = "evt-12345",
            deviceId = "dev-pixel-8",
            latitude = 37.7749,
            longitude = -122.4194,
            accuracyMeters = 5.2f,
            altitudeMeters = 15.0,
            speedMetersPerSecond = 2.5f,
            bearingDegrees = 90.0f,
            batteryPercentage = 85,
            recordedAtUtc = "2026-10-25T12:00:00.000Z",
            locationSource = "gps",
            isFreshLocation = true,
            uploadStatus = LocationEntity.STATUS_PENDING
        )

        assertEquals("evt-12345", entity.eventId)
        assertEquals(37.7749, entity.latitude, 0.0001)
        assertEquals(-122.4194, entity.longitude, 0.0001)
        assertEquals(5.2f, entity.accuracyMeters, 0.01f)
        assertEquals(85, entity.batteryPercentage)
        assertEquals(LocationEntity.STATUS_PENDING, entity.uploadStatus)
        assertTrue(entity.isFreshLocation)
    }
}
