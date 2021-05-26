#include <Arduino_LSM6DS3.h>
#include "ace_ble.hpp"


int prevMillis;
int ledCounter;

AceBle ble;

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

void setupBLE()
{
  Serial.print("Setting up BLE...");
  ble.setup();
  Serial.println("DONE");
}

void setup() {
  pinMode(LED_BUILTIN, OUTPUT); // initialize the built-in LED pin to indicate when a central is connected
  digitalWrite(LED_BUILTIN, HIGH);

  Serial.begin(9600);

  setupIMU();
  setupBLE();
}

void loop() {
  float aX, aY, aZ, gX, gY, gZ;

  int currMillis = millis();
  int delta = currMillis - prevMillis;

  if (delta > 20)
  {  
    prevMillis = currMillis;
    if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) {
      // read the acceleration and gyroscope data
      IMU.readAcceleration(aX, aY, aZ);
      IMU.readGyroscope(gX, gY, gZ);

      /*
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
      */

      ble.handle(aX, aY, aZ, gX, gY, gZ);

      if (aZ > 0.8)
        ble.alarm();
    }

    ledCounter ++;
    if (ledCounter > 100)
      ledCounter = 0;
    else if (ledCounter > 50)
      digitalWrite(LED_BUILTIN, HIGH);
    else 
      digitalWrite(LED_BUILTIN, LOW);
  }
}
