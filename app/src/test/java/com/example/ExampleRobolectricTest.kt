package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AssistantOutcome
import com.example.ai.GeminiAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nexora", appName)
  }

  @Test
  fun `test Siri-first assistant behavior when IoT mode is disabled`() {
    val assistant = GeminiAssistant()

    // When IoT mode is disabled (default), IoT requests must be rejected with an explanation
    val lightOnOutcome = assistant.parseOfflineCommand("kamre ki light on karo", isIotModeEnabled = false)
    assertTrue(lightOnOutcome is AssistantOutcome.SpokenResponse)
    val lightReply = (lightOnOutcome as AssistantOutcome.SpokenResponse).replyText
    assertTrue(lightReply.contains("IoT") || lightReply.contains("disabled"))

    val fanOffOutcome = assistant.parseOfflineCommand("pankha band karo", isIotModeEnabled = false)
    assertTrue(fanOffOutcome is AssistantOutcome.SpokenResponse)

    val tempOutcome = assistant.parseOfflineCommand("bedroom ka temperature kitna hai?", isIotModeEnabled = false)
    assertTrue(tempOutcome is AssistantOutcome.SpokenResponse)

    // Siri personal assistant commands work directly
    val callOutcome = assistant.parseOfflineCommand("Ammi ko call lagao", isIotModeEnabled = false)
    assertTrue(callOutcome is AssistantOutcome.CallAction)
    val call = callOutcome as AssistantOutcome.CallAction
    assertEquals("ammi", call.contactName.lowercase())

    val cameraOutcome = assistant.parseOfflineCommand("camera kholo", isIotModeEnabled = false)
    assertTrue(cameraOutcome is AssistantOutcome.CameraAction)

    val musicOutcome = assistant.parseOfflineCommand("gaana bajao", isIotModeEnabled = false)
    assertTrue(musicOutcome is AssistantOutcome.MusicAction)

    val alarmOutcome = assistant.parseOfflineCommand("alarm dikhao", isIotModeEnabled = false)
    assertTrue(alarmOutcome is AssistantOutcome.AlarmAction)

    val appOutcome = assistant.parseOfflineCommand("open youtube", isIotModeEnabled = false)
    assertTrue(appOutcome is AssistantOutcome.AppAction)
  }

  @Test
  fun `test smart home commands when IoT mode is enabled`() {
    val assistant = GeminiAssistant()

    // Light ON
    val lightOnOutcome = assistant.parseOfflineCommand("kamre ki light on karo", isIotModeEnabled = true)
    assertTrue(lightOnOutcome is AssistantOutcome.DeviceAction)
    val lightOn = lightOnOutcome as AssistantOutcome.DeviceAction
    assertEquals("ON", lightOn.action)

    // Fan OFF
    val fanOffOutcome = assistant.parseOfflineCommand("pankha band karo", isIotModeEnabled = true)
    assertTrue(fanOffOutcome is AssistantOutcome.DeviceAction)
    val fanOff = fanOffOutcome as AssistantOutcome.DeviceAction
    assertEquals("OFF", fanOff.action)
    assertEquals("fan", fanOff.deviceName)

    // Sensor temperature query
    val tempOutcome = assistant.parseOfflineCommand("bedroom ka temperature kitna hai?", isIotModeEnabled = true)
    assertTrue(tempOutcome is AssistantOutcome.SensorQuery)

    // Routines
    val routineOutcome = assistant.parseOfflineCommand("saari lights band karo", isIotModeEnabled = true)
    assertTrue(routineOutcome is AssistantOutcome.RoutineAction)
    val routine = routineOutcome as AssistantOutcome.RoutineAction
    assertEquals("All Off", routine.routineName)
  }
}
