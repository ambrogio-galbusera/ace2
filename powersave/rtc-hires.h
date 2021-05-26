#ifndef RTC_HIRES_H
#define RTC_HIRES_H

#include "Arduino.h"


class RTCHiRes {
public:
  RTCHiRes();
  
  void microsleep(uint32_t micros);
  
};

#endif
