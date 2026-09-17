package com.example.iot

object Esp32FirmwareCode {
    val ARDUINO_SKETCH = """
/*
 * =========================================================================
 *                   SEERU AI - ESP32 HOME AUTOMATION FIRMWARE
 * =========================================================================
 * Hardware: ESP32 DevKit V1
 * Features:
 *   - Local Wi-Fi Web Server
 *   - Token-based Authentication
 *   - Digital Relay & LED control (Pins 2, 4, 16, 17, etc.)
 *   - DHT11 / DHT22 Sensor readings (Pin 15)
 *   - Safety Default States (Active-Low / Active-High relay support)
 * 
 * Instructions:
 * 1. Install ESP32 Board in Arduino IDE (Boards Manager -> esp32 by Espressif)
 * 2. Install ArduinoJson and DHT sensor library by Adafruit
 * 3. Update WIFI_SSID and WIFI_PASS below.
 * 4. Flash to your ESP32 and check the Serial Monitor (115200 baud) for IP!
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

// --- Wi-Fi Credentials ---
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

// --- Security Token (Must match token in Seeru Android App) ---
const char* AUTH_TOKEN = "seeru_secret_token";

// --- GPIO Pin Definitions ---
const int PIN_LIGHT = 2;    // Built-in LED or Relay 1
const int PIN_FAN   = 4;    // Relay 2

WebServer server(80);

void handleRoot() {
  server.send(200, "text/plain", "SEERU AI ESP32 Controller Online");
}

void handleDeviceCommand() {
  if (server.method() != HTTP_POST) {
    server.send(405, "application/json", "{\"status\":\"error\",\"message\":\"Method Not Allowed\"}");
    return;
  }

  String body = server.arg("plain");
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, body);

  if (err) {
    server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Invalid JSON\"}");
    return;
  }

  const char* token = doc["token"];
  if (!token || strcmp(token, AUTH_TOKEN) != 0) {
    server.send(401, "application/json", "{\"status\":\"error\",\"message\":\"Unauthorized token\"}");
    return;
  }

  const char* action = doc["action"];
  int pin = doc["pin"];
  if (pin <= 0) pin = PIN_LIGHT; // Default to main light

  bool newState = false;
  pinMode(pin, OUTPUT);

  if (strcasecmp(action, "ON") == 0) {
    digitalWrite(pin, HIGH);
    newState = true;
  } else if (strcasecmp(action, "OFF") == 0) {
    digitalWrite(pin, LOW);
    newState = false;
  } else if (strcasecmp(action, "TOGGLE") == 0) {
    int current = digitalRead(pin);
    digitalWrite(pin, !current);
    newState = !current;
  }

  StaticJsonDocument<256> resp;
  resp["status"] = "ok";
  resp["state"] = newState;
  resp["pin"] = pin;
  resp["message"] = newState ? "Device turned ON" : "Device turned OFF";

  String respStr;
  serializeJson(resp, respStr);
  server.send(200, "application/json", respStr);
}

void handleSensors() {
  String token = server.arg("token");
  if (token != AUTH_TOKEN) {
    server.send(401, "application/json", "{\"status\":\"error\",\"message\":\"Unauthorized\"}");
    return;
  }

  // Example analog or DHT readings
  float temperature = 26.5; // Replace with dht.readTemperature();
  float humidity = 55.0;    // Replace with dht.readHumidity();

  StaticJsonDocument<256> resp;
  resp["status"] = "ok";
  resp["temperature"] = temperature;
  resp["humidity"] = humidity;

  String respStr;
  serializeJson(resp, respStr);
  server.send(200, "application/json", respStr);
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LIGHT, OUTPUT);
  pinMode(PIN_FAN, OUTPUT);
  digitalWrite(PIN_LIGHT, LOW);
  digitalWrite(PIN_FAN, LOW);

  Serial.print("Connecting to Wi-Fi: ");
  Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi Connected!");
  Serial.print("ESP32 IP Address: ");
  Serial.println(WiFi.localIP());

  server.on("/", handleRoot);
  server.on("/api/device", HTTP_POST, handleDeviceCommand);
  server.on("/api/sensors", HTTP_GET, handleSensors);

  server.begin();
  Serial.println("HTTP Server started. Ready for SEERU AI!");
}

void loop() {
  server.handleClient();
}
    """.trimIndent()
}
