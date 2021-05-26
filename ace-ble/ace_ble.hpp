#ifndef __ACEBLE__
#define __ACEBLE__

#include <Arduino.h>

class AceBle {
public:
  AceBle ():
    status(0)
    {}
  
  // Empty destructor
  ~AceBle() = default; 

  bool setup();
  void handle(float x, float y, float z, float gx, float gy, float gz);
  void alarm();
  
private:
  int status;
};

#endif
