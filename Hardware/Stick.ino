#include <WiFi.h>
#include <Wire.h>
#include <math.h>
#include <time.h>
#include <Firebase_ESP_Client.h>

#include <Adafruit_VL53L0X.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_ADXL345_U.h>

// Firebase Addons
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ================= WIFI =================
#define WIFI_SSID "wifi username"
#define WIFI_PASSWORD "wifi password"

// ================= FIREBASE =================
#define API_KEY "firebase apikey"
#define DATABASE_URL "firebase database url"

// Firebase Authentication
#define USER_EMAIL "email id"
#define USER_PASSWORD "user password"

// ================= FIREBASE OBJECTS =================
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ================= SENSOR OBJECTS =================
Adafruit_VL53L0X lidarSensor = Adafruit_VL53L0X();
Adafruit_ADXL345_Unified accel = Adafruit_ADXL345_Unified(12345);

// ================= ESP32 PINS =================
#define WATER_AO_PIN         34
#define IR_PIN               27
#define BUZZER_PIN           25
#define EMERGENCY_SWITCH_PIN 13   // Rocker switch / emergency switch

#define SDA_PIN 21
#define SCL_PIN 22

// ================= FALL THRESHOLDS =================
float freeFallThreshold = 5.0;    // m/s²
float impactThreshold   = 25.0;   // m/s²

// ================= LIDAR THRESHOLD =================
#define OBSTACLE_THRESHOLD 800   // mm

// ================= VARIABLES =================
bool emergencyAlreadySent = false;

// ================= BUZZER FUNCTION =================
void beep(int onTime, int offTime)
{
  digitalWrite(BUZZER_PIN, HIGH);
  delay(onTime);
  digitalWrite(BUZZER_PIN, LOW);
  delay(offTime);
}

// ================= SEND SENSOR DATA TO FIREBASE =================
void sendToFirebase(
  bool fallDetected,
  int lidarValue,
  int lidarDistance,
  bool obstacleDetected,
  String obstacleStatus,
  int waterValue,
  bool waterDetected,
  String waterStatus,
  float accelX,
  float accelY,
  float accelZ,
  float accelMagnitude
)
{
  bool ok = true;

  // Existing sensor fields
  ok &= Firebase.RTDB.setBool(&fbdo, "/Sensor/fallDetected", fallDetected);

  // 🔥 NEW ADDED VALUE
  ok &= Firebase.RTDB.setString(&fbdo, "/Sensor/obstacle_status", obstacleStatus);

  ok &= Firebase.RTDB.setInt(&fbdo, "/Sensor/lidar", lidarValue);            // mm
  ok &= Firebase.RTDB.setInt(&fbdo, "/Sensor/lidarDistance", lidarDistance); // mm

  ok &= Firebase.RTDB.setBool(&fbdo, "/Sensor/obstacleDetected", obstacleDetected);

  ok &= Firebase.RTDB.setInt(&fbdo, "/Sensor/water", waterValue);
  ok &= Firebase.RTDB.setBool(&fbdo, "/Sensor/waterDetected", waterDetected);
  ok &= Firebase.RTDB.setString(&fbdo, "/Sensor/waterStatus", waterStatus);
  ok &= Firebase.RTDB.setInt(&fbdo, "/Sensor/waterValue", waterValue);

  // ✅ Accelerometer directly under /Sensor
  ok &= Firebase.RTDB.setFloat(&fbdo, "/Sensor/x", accelX);
  ok &= Firebase.RTDB.setFloat(&fbdo, "/Sensor/y", accelY);
  ok &= Firebase.RTDB.setFloat(&fbdo, "/Sensor/z", accelZ);
  ok &= Firebase.RTDB.setFloat(&fbdo, "/Sensor/magnitude", accelMagnitude);
  ok &= Firebase.RTDB.setInt(&fbdo, "/Sensor/timestamp", millis());

  if (ok)
  {
    Serial.println("✅ Live Sensor Data Sent to Firebase");
  }
  else
  {
    Serial.println("❌ Firebase Live Sensor Data Error");
    Serial.println(fbdo.errorReason());
  }
}

// ================= SEND EMERGENCY TO FIREBASE =================
void sendEmergencyToFirebase(bool emergencyPressed)
{
  bool ok = true;

  ok &= Firebase.RTDB.setBool(&fbdo, "/Sensor/emergencyPressed", emergencyPressed);

  if (emergencyPressed)
  {
    ok &= Firebase.RTDB.setString(&fbdo, "/Sensor/emergencyMessage", "Blind person pressed emergency switch");
  }
  else
  {
    ok &= Firebase.RTDB.setString(&fbdo, "/Sensor/emergencyMessage", "Emergency cleared");
  }

  if (ok)
  {
    Serial.println("✅ Emergency Data Sent to Firebase");
  }
  else
  {
    Serial.println("❌ Firebase Emergency Error");
    Serial.println(fbdo.errorReason());
  }
}

// ================= CHECK EMERGENCY SWITCH =================
void handleEmergencySwitch()
{
  // INPUT_PULLUP logic:
  // Pressed / ON = LOW
  // Released / OFF = HIGH
  int switchState = digitalRead(EMERGENCY_SWITCH_PIN);

  if (switchState == LOW)
  {
    if (!emergencyAlreadySent)
    {
      Serial.println("🚨 EMERGENCY SWITCH PRESSED!");

      // Confirmation beep
      beep(200, 100);
      beep(200, 100);
      beep(200, 100);

      sendEmergencyToFirebase(true);
      emergencyAlreadySent = true;
    }
  }
  else
  {
    if (emergencyAlreadySent)
    {
      Serial.println("✅ Emergency Switch Released");

      sendEmergencyToFirebase(false);
      emergencyAlreadySent = false;
    }
  }
}

// ================= SETUP =================
void setup()
{
  Serial.begin(115200);

  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(IR_PIN, INPUT);
  pinMode(WATER_AO_PIN, INPUT);
  pinMode(EMERGENCY_SWITCH_PIN, INPUT_PULLUP);

  Wire.begin(SDA_PIN, SCL_PIN);

  // ================= WIFI CONNECT =================
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");

  while (WiFi.status() != WL_CONNECTED)
  {
    Serial.print(".");
    delay(500);
  }

  Serial.println("\n✅ WiFi Connected");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  // ================= TIME SYNC =================
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");

  Serial.print("Syncing Time");
  time_t now = time(nullptr);
  while (now < 100000)
  {
    Serial.print(".");
    delay(500);
    now = time(nullptr);
  }
  Serial.println("\n✅ Time Synced");

  // ================= FIREBASE CONFIG =================
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  config.token_status_callback = tokenStatusCallback;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  Serial.print("Waiting for Firebase");
  while (!Firebase.ready())
  {
    Serial.print(".");
    delay(300);
  }
  Serial.println("\n🔥 Firebase Ready");

  // ================= INITIAL FIREBASE VALUES =================
  Firebase.RTDB.setBool(&fbdo, "/Sensor/emergencyPressed", false);
  Firebase.RTDB.setString(&fbdo, "/Sensor/emergencyMessage", "System ready");
  Firebase.RTDB.setBool(&fbdo, "/Sensor/fallDetected", false);
  Firebase.RTDB.setString(&fbdo, "/Sensor/obstacle_status", "System ready");

  // ================= LIDAR INIT =================
  if (!lidarSensor.begin())
  {
    Serial.println("❌ LiDAR (VL53L0X) Not Found");
    while (1);
  }

  // ================= ADXL345 INIT =================
  if (!accel.begin())
  {
    Serial.println("❌ ADXL345 Not Found");
    while (1);
  }

  accel.setRange(ADXL345_RANGE_16_G);

  Serial.println("✅ All Sensors Ready");
}

// ================= LOOP =================
void loop()
{
  // =====================================================
  // 0) CHECK EMERGENCY SWITCH FIRST (HIGH PRIORITY)
  // =====================================================
  handleEmergencySwitch();

  // =====================================================
  // 1) WATER SENSOR
  // =====================================================
  int waterValue = analogRead(WATER_AO_PIN);
  String waterStatus = "Dry";
  bool waterDetected = false;

  // Adjust thresholds based on your real sensor values
  if (waterValue > 3000)
  {
    waterStatus = "Dry";
    waterDetected = false;
  }
  else if (waterValue > 1500)
  {
    waterStatus = "Wet";
    waterDetected = true;

    Serial.println("💧 Water Detected");
    beep(300, 200);
  }
  else
  {
    waterStatus = "Deep Water";
    waterDetected = true;

    Serial.println("🌊 Deep Water Detected");
    beep(700, 200);
  }

  Serial.print("Water Value: ");
  Serial.print(waterValue);
  Serial.print(" -> ");
  Serial.println(waterStatus);

  // =====================================================
  // 2) IR SENSOR
  // =====================================================
  bool obstacleDetected = (digitalRead(IR_PIN) == LOW);

  if (obstacleDetected)
  {
    Serial.println("⚠ IR Obstacle Detected");
    beep(150, 150);
  }
  else
  {
    Serial.println("✅ No IR Obstacle");
  }

  // =====================================================
  // 3) LIDAR SENSOR (IN MM) + obstacle_status
  // =====================================================
  int distance = 0;
  String obstacleStatus = "Out of Range";

  VL53L0X_RangingMeasurementData_t measure;
  lidarSensor.rangingTest(&measure, false);

  if (measure.RangeStatus != 4)
  {
    distance = measure.RangeMilliMeter;   // IN MM

    Serial.print("📏 LiDAR Distance: ");
    Serial.print(distance);
    Serial.println(" mm");

    // 🔥 NEW obstacle_status logic
    if (distance <= OBSTACLE_THRESHOLD)
    {
      obstacleStatus = "Obstacle Detected";
    }
    else
    {
      obstacleStatus = "No Obstacle";
    }

    // Existing buzzer logic based on distance in mm
    if (distance < 200)
    {
      Serial.println("🚨 Very Close Object");
      beep(700, 200);
    }
    else if (distance < 500)
    {
      Serial.println("⚠ Nearby Object");
      beep(350, 200);
    }
    else if (distance < 1000)
    {
      Serial.println("🟡 Object Far");
      beep(150, 200);
    }
    else
    {
      Serial.println("✅ Path Clear");
    }
  }
  else
  {
    Serial.println("❌ LiDAR Out of Range");
    distance = 0;
    obstacleStatus = "Out of Range";
  }

  Serial.print("Obstacle Status: ");
  Serial.println(obstacleStatus);

  // =====================================================
  // 4) ACCELEROMETER (ADXL345) + FALL DETECTION
  // =====================================================
  bool fallDetected = false;

  sensors_event_t event;
  accel.getEvent(&event);

  float accelX = event.acceleration.x;
  float accelY = event.acceleration.y;
  float accelZ = event.acceleration.z;

  float magnitude = sqrt(
    accelX * accelX +
    accelY * accelY +
    accelZ * accelZ
  );

  Serial.print("Accel X: ");
  Serial.print(accelX);
  Serial.print(" | Y: ");
  Serial.print(accelY);
  Serial.print(" | Z: ");
  Serial.print(accelZ);
  Serial.print(" | Magnitude: ");
  Serial.println(magnitude);

  // Free-fall + impact detection
  if (magnitude < freeFallThreshold)
  {
    Serial.println("⚠ Possible Free Fall...");
    delay(300);

    accel.getEvent(&event);

    float impactX = event.acceleration.x;
    float impactY = event.acceleration.y;
    float impactZ = event.acceleration.z;

    float impactMag = sqrt(
      impactX * impactX +
      impactY * impactY +
      impactZ * impactZ
    );

    Serial.print("Impact Magnitude: ");
    Serial.println(impactMag);

    if (impactMag > impactThreshold)
    {
      fallDetected = true;
      Serial.println("🚨 FALL DETECTED!");
      beep(200, 200);
      delay(500);

      Firebase.RTDB.setBool(&fbdo, "/Sensor/fallDetected", true);
      Firebase.RTDB.setString(&fbdo, "/Sensor/fallMessage", "Fall detected by accelerometer");
    }
  }

  // =====================================================
  // 5) SEND ALL LIVE DATA TO FIREBASE
  // =====================================================
  sendToFirebase(
    fallDetected,
    distance,
    distance,
    obstacleDetected,
    obstacleStatus,
    waterValue,
    waterDetected,
    waterStatus,
    accelX,
    accelY,
    accelZ,
    magnitude
  );
  Serial.println("-----------------------------");
  delay(1000);
}
