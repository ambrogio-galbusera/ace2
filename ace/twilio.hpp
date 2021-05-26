#ifndef __TWILIO__
#define __TWILIO__

// From https://github.com/TwilioDevEd/twilio_esp8266_arduino_example

#include <Arduino.h>
#include "url_coding.hpp"

class Twilio {
public:
        Twilio(
                const char* account_sid_in, 
                const char* auth_token_in
        )
                : account_sid(account_sid_in)
                , auth_token(auth_token_in)
        {}
        // Empty destructor
        ~Twilio() = default; 

        bool sendMessage(
                const String& toNumber,
                const String& fromNumber,
                const String& messageBody,
                String& response
        );

private:
        // Account SID and Auth Token come from the Twilio console.
        // See: https://twilio.com/console for more.

        // Used for the username of the auth header
        String account_sid;
        // Used for the password of the auth header
        String auth_token;

        // Utilities
        static String _getAuthHeader(
                const String& user, 
                const String& password
        );
        
};
#endif
