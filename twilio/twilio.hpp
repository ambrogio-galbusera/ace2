#ifndef __TWILIO__
#define __TWILIO__

// From https://github.com/TwilioDevEd/twilio_esp8266_arduino_example

#include <Arduino.h>
#include "url_coding.hpp"

class Twilio {
public:
        Twilio() {}

        // Empty destructor
        ~Twilio() = default; 

        void begin(
                const char* account_sid_in, 
                const char* auth_token_in);

        bool send_message(
                const String& to_number,
                const String& from_number,
                const String& message_body,
                String& response,
                const String& picture_url = ""
        );

private:
        // Account SID and Auth Token come from the Twilio console.
        // See: https://twilio.com/console for more.

        // Used for the username of the auth header
        String account_sid;
        // Used for the password of the auth header
        String auth_token;

        // Utilities
        static String _get_auth_header(
                const String& user, 
                const String& password
        );
        
};
#endif
