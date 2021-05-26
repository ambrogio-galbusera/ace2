#include <Arduino.h>
#include <Arduino_LSM6DS3.h>
#include "rtc-hires.h"


int status = 0;
RTCHiRes rhr;

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  
  // put your setup code here, to run once:
  IMU.begin();
  delay(20000);
}

void loop() {
  float aX, aY, aZ, gX, gY, gZ;

  rhr.microsleep(20000);

  digitalWrite(LED_BUILTIN, HIGH);
  if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) {
    // read the acceleration and gyroscope data
    IMU.readAcceleration(aX, aY, aZ);
    IMU.readGyroscope(gX, gY, gZ);
  }
  status = !status;
  digitalWrite(LED_BUILTIN, LOW);
}
