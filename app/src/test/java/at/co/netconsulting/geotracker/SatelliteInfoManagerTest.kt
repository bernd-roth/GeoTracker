package at.co.netconsulting.geotracker

import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import at.co.netconsulting.geotracker.data.SatelliteInfo
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SatelliteInfoManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockGnssStatus: GnssStatus
    private lateinit var satelliteInfoManager: SatelliteInfoManager

    @Before
    fun setUp() {
        // Create mocks
        mockContext = mockk()
        mockLocationManager = mockk(relaxed = true)
        mockGnssStatus = mockk()

        // Setup context to return location manager
        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager

        // Mock the permission check - this is what ContextCompat.checkSelfPermission actually calls
        every { mockContext.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, any(), any()) } returns PackageManager.PERMISSION_GRANTED

        // Create instance under test
        satelliteInfoManager = SatelliteInfoManager(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test initial satellite info state`() {
        // Verify initial state
        val initialState = satelliteInfoManager.currentSatelliteInfo.value
        assertEquals(0, initialState.visibleSatellites)
        assertEquals(0, initialState.totalSatellites)
    }

    @Test
    fun `test stopListening`() {
        // Test
        satelliteInfoManager.stopListening()

        // Verify unregistration was called
        verify { mockLocationManager.unregisterGnssStatusCallback(any()) }
    }

    @Test
    fun `test startListening with permission granted`() {
        // Test
        satelliteInfoManager.startListening()

        // Verify registration was called
        verify { mockLocationManager.registerGnssStatusCallback(any(), null) }
    }

    @Test
    fun `test startListening with permission denied`() {
        // Override the permission check to return denied
        every { mockContext.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, any(), any()) } returns PackageManager.PERMISSION_DENIED

        // Test
        satelliteInfoManager.startListening()

        // Verify registration was NOT called
        verify(exactly = 0) { mockLocationManager.registerGnssStatusCallback(any(), null) }
    }

    @Test
    fun `test gnss callback updates satellite info correctly`() = runTest {
        // Setup mock GNSS status
        val totalSatellites = 5
        val usedSatellites = listOf(true, false, true, true, false) // 3 used satellites

        every { mockGnssStatus.satelliteCount } returns totalSatellites
        every { mockGnssStatus.usedInFix(0) } returns usedSatellites[0]
        every { mockGnssStatus.usedInFix(1) } returns usedSatellites[1]
        every { mockGnssStatus.usedInFix(2) } returns usedSatellites[2]
        every { mockGnssStatus.usedInFix(3) } returns usedSatellites[3]
        every { mockGnssStatus.usedInFix(4) } returns usedSatellites[4]

        var capturedCallback: GnssStatus.Callback? = null
        every { mockLocationManager.registerGnssStatusCallback(capture(slot<GnssStatus.Callback>()), null) } answers {
            capturedCallback = firstArg()
            true
        }

        // Start listening to capture the callback
        satelliteInfoManager.startListening()

        // Verify callback was captured
        assertNotNull(capturedCallback)

        // Trigger the callback
        capturedCallback!!.onSatelliteStatusChanged(mockGnssStatus)

        // Verify state was updated correctly
        val updatedState = satelliteInfoManager.currentSatelliteInfo.value
        assertEquals(3, updatedState.visibleSatellites) // 3 satellites used in fix
        assertEquals(5, updatedState.totalSatellites)
    }

    @Test
    fun `test gnss callback with no satellites used in fix`() = runTest {
        // Setup mock GNSS status with no satellites used
        val totalSatellites = 3
        every { mockGnssStatus.satelliteCount } returns totalSatellites
        every { mockGnssStatus.usedInFix(any()) } returns false

        // Setup and capture callback
        var capturedCallback: GnssStatus.Callback? = null
        every { mockLocationManager.registerGnssStatusCallback(capture(slot<GnssStatus.Callback>()), null) } answers {
            capturedCallback = firstArg()
            true
        }

        satelliteInfoManager.startListening()
        capturedCallback!!.onSatelliteStatusChanged(mockGnssStatus)

        // Verify state
        val updatedState = satelliteInfoManager.currentSatelliteInfo.value
        assertEquals(0, updatedState.visibleSatellites)
        assertEquals(3, updatedState.totalSatellites)
    }

    @Test
    fun `test gnss callback with all satellites used in fix`() = runTest {
        // Setup mock GNSS status with all satellites used
        val totalSatellites = 4
        every { mockGnssStatus.satelliteCount } returns totalSatellites
        every { mockGnssStatus.usedInFix(any()) } returns true

        // Setup and capture callback
        var capturedCallback: GnssStatus.Callback? = null
        every { mockLocationManager.registerGnssStatusCallback(capture(slot<GnssStatus.Callback>()), null) } answers {
            capturedCallback = firstArg()
            true
        }

        satelliteInfoManager.startListening()
        capturedCallback!!.onSatelliteStatusChanged(mockGnssStatus)

        // Verify state
        val updatedState = satelliteInfoManager.currentSatelliteInfo.value
        assertEquals(4, updatedState.visibleSatellites)
        assertEquals(4, updatedState.totalSatellites)
    }

    @Test
    fun `test multiple gnss callback updates`() = runTest {
        // Setup callback capture
        var capturedCallback: GnssStatus.Callback? = null
        every { mockLocationManager.registerGnssStatusCallback(capture(slot<GnssStatus.Callback>()), null) } answers {
            capturedCallback = firstArg()
            true
        }

        satelliteInfoManager.startListening()

        // First update
        every { mockGnssStatus.satelliteCount } returns 3
        every { mockGnssStatus.usedInFix(0) } returns true
        every { mockGnssStatus.usedInFix(1) } returns false
        every { mockGnssStatus.usedInFix(2) } returns true

        capturedCallback!!.onSatelliteStatusChanged(mockGnssStatus)

        var state = satelliteInfoManager.currentSatelliteInfo.value
        assertEquals(2, state.visibleSatellites)
        assertEquals(3, state.totalSatellites)

        // Second update
        every { mockGnssStatus.satelliteCount } returns 5
        every { mockGnssStatus.usedInFix(0) } returns true
        every { mockGnssStatus.usedInFix(1) } returns true
        every { mockGnssStatus.usedInFix(2) } returns true
        every { mockGnssStatus.usedInFix(3) } returns false
        every { mockGnssStatus.usedInFix(4) } returns true

        capturedCallback!!.onSatelliteStatusChanged(mockGnssStatus)

        state = satelliteInfoManager.currentSatelliteInfo.value
        assertEquals(4, state.visibleSatellites)
        assertEquals(5, state.totalSatellites)
    }
}

// Alternative approach: Test the class with a test double that doesn't depend on Android framework
class TestSatelliteInfoManager(context: Context) {
    private var _currentSatelliteInfo = kotlinx.coroutines.flow.MutableStateFlow(SatelliteInfo())
    val currentSatelliteInfo: kotlinx.coroutines.flow.StateFlow<SatelliteInfo> = _currentSatelliteInfo.asStateFlow()

    // Test method to simulate satellite status changes without Android dependencies
    fun simulateSatelliteStatusChange(totalSatellites: Int, usedSatellites: BooleanArray) {
        var visibleCount = 0

        for (i in 0 until totalSatellites) {
            if (i < usedSatellites.size && usedSatellites[i]) {
                visibleCount++
            }
        }

        _currentSatelliteInfo.value = SatelliteInfo(
            visibleSatellites = visibleCount,
            totalSatellites = totalSatellites
        )
    }
}

class TestSatelliteInfoManagerUnitTest {

    @Test
    fun `test satellite counting logic isolated`() {
        val mockContext = mockk<Context>()
        val testManager = TestSatelliteInfoManager(mockContext)

        // Test with mixed satellite usage
        testManager.simulateSatelliteStatusChange(5, booleanArrayOf(true, false, true, true, false))

        val state = testManager.currentSatelliteInfo.value
        assertEquals(3, state.visibleSatellites)
        assertEquals(5, state.totalSatellites)
    }

    @Test
    fun `test satellite counting with no satellites used`() {
        val mockContext = mockk<Context>()
        val testManager = TestSatelliteInfoManager(mockContext)

        testManager.simulateSatelliteStatusChange(4, booleanArrayOf(false, false, false, false))

        val state = testManager.currentSatelliteInfo.value
        assertEquals(0, state.visibleSatellites)
        assertEquals(4, state.totalSatellites)
    }

    @Test
    fun `test satellite counting with all satellites used`() {
        val mockContext = mockk<Context>()
        val testManager = TestSatelliteInfoManager(mockContext)

        testManager.simulateSatelliteStatusChange(3, booleanArrayOf(true, true, true))

        val state = testManager.currentSatelliteInfo.value
        assertEquals(3, state.visibleSatellites)
        assertEquals(3, state.totalSatellites)
    }
}