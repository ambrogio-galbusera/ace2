#include <ArduinoBLE.h>
#include "ace_ble.hpp"

#define BLE_WAITCONN_TIMEOUT_MS 60000

///////////////////////////////////////////////////////////////////////////////////
// BLE
// Define custom BLE service for position (read-only)
BLEService posService("95ff7bf8-aa6f-4671-82d9-22a8931c5387");
BLEStringCharacteristic pos("8f0e70fc-2a4b-4fd3-b487-6babf2e220e1", BLERead | BLENotify, 30);

// Define custom BLE service for alarm notification 
BLEService hbService("daaac223-ea6d-411f-8d8e-32bfb46d4bad");
BLEByteCharacteristic hb("40369706-e126-4563-b7ae-3a34b45b3ab8", BLERead | BLENotify);

bool AceBle::setup()
{
  // Initialize BLE
  if(!BLE.begin()) {
    // Failed to initialize BLE, blink the internal LED
    Serial.println("AceBle: Failed initializing BLE");
    return false;
  }

  // Set advertised local name and services UUID
  BLE.setDeviceName("Arduino Nano 33 IoT");
  BLE.setLocalName("ACE");

  posService.addCharacteristic(pos);
  BLE.addService(posService);

  hbService.addCharacteristic(hb);
  BLE.addService(hbService);

  // Set default values for characteristics
  hb.writeValue(0);

  // Start advertising
  BLE.advertise();
  return true;
}

void AceBle::handle(float x, float y, float z, float gx, float gy, float gz)
{
  /*
  if (millis() > BLE_WAITCONN_TIMEOUT_MS)  
  {
    if (!BLE.connected())
      return;
  }
  */
  
  if (!BLE.connected())
    return;

  // Write values on BLE
  String s;

  if (status == 0)
  {
    s += "A;";
    s += String(x, 3);
    s += ";";
    s += String(y, 3);
    s += ";";
    s += String(z, 3);
    s += ";";
  }
  else
  {
    s += "G;";
    s += String((int)gx);
    s += ";";
    s += String((int)gy);
    s += ";";
    s += String((int)gz);
    s += ";";
  }

  status = (status+1) % 2;

  pos.writeValue(s);
  Serial.println(s);
}

void  AceBle::alarm() {
  hb.writeValue(1);
}
