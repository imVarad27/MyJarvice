package com.example.myjarvice.ui.main

import com.example.myjarvice.data.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun connectionStatus_hasExpectedStates() {
    assertEquals(ConnectionStatus.DISCONNECTED, ConnectionStatus.valueOf("DISCONNECTED"))
  }

  @Test
  fun connectionStatus_includesFailureState() {
    assertEquals(ConnectionStatus.ERROR, ConnectionStatus.valueOf("ERROR"))
  }
}
