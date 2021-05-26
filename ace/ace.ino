// Use software serial for debugging?
#define USE_SOFTWARE_SERIAL 0

// Print debug messages over serial?
#define USE_SERIAL 1

#include <WiFiNINA.h>
#include <Arduino_LSM6DS3.h>


#include "rtc-hires.h"
#include "twilio.hpp"
#include "ace_ble.hpp"
#include "fall_detector.hpp"

///////////////////////////////////////////////////////////////////////////////////
// Wifi parameter
// Set these - but DON'T push them to GitHub!
static const char *ssid = "Your wifi";
static const char *password = "Wifi password";

///////////////////////////////////////////////////////////////////////////////////
// Values from Twilio (find them on the dashboard)
static const char *account_sid = "Twilio account sid";
static const char *auth_token = "Twilio account token";
// Phone number should start with "+<countrycode>"
static const char *from_number = "Twilio phone number";

// Phone number should start with "+<countrycode>"
static const char *to_number = "Your phone number";
static const char *message = "ALERT! I felt down and I need help";

Twilio *twilio;

///////////////////////////////////////////////////////////////////////////////////
// BLE
AceBle ble;

///////////////////////////////////////////////////////////////////////////////////
// Fall detector
AceFallDetector fallDetector;

///////////////////////////////////////////////////////////////////////////////////
// RTC
RTCHiRes rhr;

// 
#define APPSTATUS_NORMAL    0
#define APPSTATUS_ALARM     1
#define APPSTATUS_ALARM_WAITING 2
#define APPSTATUS_ALARM_SENT 3

int prevMillis;
int startMillis;
int appStatus;
int smsStartMillis;
int ledsCounter;
int LEDS_COUNTER[] = { 50, 5, 5, 0 };

void setupFD(){
  Serial.print("Setting up TF...");
  fallDetector.setup();
  Serial.println("DONE");
}

void setupIMU() {
  // Initialize IMU
  Serial.print("Setting up IMU...");
  if (!IMU.begin()) {
    // Failed to initialize IMU, blink the internal LED
    Serial.println("Failed initializing IMU");
    while (1) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(100);
      digitalWrite(LED_BUILTIN, LOW);
      delay(100);
    }
  }
  Serial.println("DONE");
}

void setupBLE() {
  Serial.print("Setting up BLE...");
  if(!ble.setup()) {
    // Failed to initialize BLE, blink the internal LED
    Serial.println("Failed initializing BLE");
    while (1) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(100);
      digitalWrite(LED_BUILTIN, LOW);
      delay(100);
    }
  }
  Serial.println("DONE");
}

void setupTwilio()
{
  Serial.print("Setting up Twilio...");
  twilio = new Twilio(account_sid, auth_token);
  Serial.println("DONE");
}

void setup() {
  pinMode(LED_BUILTIN, OUTPUT); // initialize the built-in LED pin to indicate when a central is connected
  digitalWrite(LED_BUILTIN, HIGH);

  Serial.begin(9600);

  ledsCounter = 0;
  appStatus = APPSTATUS_NORMAL;
  
  setupFD();
  setupIMU();
  setupBLE();
  setupTwilio();

  startMillis = millis();
}

bool startWifi()
{
  Serial.print("Connecting to WiFi network ;");
  Serial.print(ssid);
  Serial.println("'...");

  WiFi.begin(ssid, password);

  int attempts = 10;
  while (WiFi.status() != WL_CONNECTED) {
    Serial.println("Connecting...");
    delay(500);
  }
  
  Serial.println("Connected!");
}

void endWifi()
{
  WiFi.end();  
}

void(* resetFunc) (void) = 0;

void loop() 
{
  int currMillis = millis();
  int delta = currMillis - prevMillis;

  if (delta > 20)
  {  
    prevMillis = currMillis;

    if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) 
    {
      // read the acceleration and gyroscope data
      float aX, aY, aZ, gX, gY, gZ;

      IMU.readAcceleration(aX, aY, aZ);
      IMU.readGyroscope(gX, gY, gZ);
      
#ifdef LOG_IMU
      Serial.print(aX);
      Serial.print(",");
      Serial.print(aY);
      Serial.print(",");
      Serial.print(aZ);
      Serial.print(";");
      Serial.print(gX);
      Serial.print(",");
      Serial.print(gY);
      Serial.print(",");
      Serial.print(gZ);
      Serial.println(";");
#endif

      // update BLE data
      ble.handle(aX, aY, aZ, gX, gY, gZ);

      if (fallDetector.addSample(aX, aY, aZ, gX, gY, gZ))
      {
        digitalWrite(LED_BUILTIN, HIGH);
        ble.alarm();

        appStatus = APPSTATUS_ALARM;
      }
    }

    if (appStatus == APPSTATUS_ALARM)
    {
      appStatus = APPSTATUS_ALARM_WAITING;
      smsStartMillis = millis();
    }

    if (appStatus == APPSTATUS_ALARM_WAITING)
    {
      int delta = millis() - smsStartMillis;
      if (delta > 25000)
      {
        startWifi();
  
        String response;
        bool success = twilio->sendMessage(to_number, from_number, message, response);
        if (success) {
          Serial.println("Sent message successfully!");
        } else {
          Serial.println(response);
        }
  
        endWifi();
        appStatus = APPSTATUS_ALARM_SENT;
      }
    }

    ledsCounter ++;
    if (ledsCounter > LEDS_COUNTER[appStatus])
      ledsCounter = 0;
      
    if (ledsCounter > LEDS_COUNTER[appStatus] / 2)
    {
      digitalWrite(LED_BUILTIN, HIGH);
    }
    else
    {
      digitalWrite(LED_BUILTIN, LOW);
    }

    if ( (appStatus == APPSTATUS_NORMAL) && (startMillis - currMillis > 20000) && (!ble.connected()) )
    {
      rhr.microsleep(16000);
    }
    else
    {
      delay(16);
    }
  }
}
