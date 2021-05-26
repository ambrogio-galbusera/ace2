// Use software serial for debugging?
#define USE_SOFTWARE_SERIAL 0

// Print debug messages over serial?
#define USE_SERIAL 1

#include "twilio.hpp"
#include <WiFiNINA.h>

// Set these - but DON'T push them to GitHub!
static const char *ssid = "<YOUR SSID HERE>";
static const char *password = "<YOUR PASSWORD HERE>";

// Values from Twilio (find them on the dashboard)
static const char *account_sid = "<ACCOUNT SID HERE>";
static const char *auth_token = "<AUTH TOKEN HERE>";
// Phone number should start with "+<countrycode>"
static const char *from_number = "<TWILIO PHONE NUMBER>";

// You choose!
// Phone number should start with "+<countrycode>"
static const char *to_number = "<DESTINATION PHONE NUMBER>";
static const char *message = "Sent from my Twilio";

Twilio twilio;

void setup() {
  Serial.begin(115200);
  Serial.print("Connecting to WiFi network ;");
  Serial.print(ssid);
  Serial.println("'...");
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    Serial.println("Connecting...");
    delay(500);
  }
  Serial.println("Connected!");

  twilio.begin(account_sid, auth_token);

  delay(1000);
  String response;
  bool success = twilio.send_message(to_number, from_number, message, response);
  if (success) {
    Serial.println("Sent message successfully!");
  } else {
    Serial.println(response);
  }
}

void loop() {
} 
