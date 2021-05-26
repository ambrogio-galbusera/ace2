
#include <Arduino.h>
#include "fall_detector.hpp"

#define FALLDET_DEG_SCALE           100.0
#define FALLDET_FREEFALL_THRESHOLD  0.4
#define FALLDET_IMPACT_THRESHOLD    1.4
#define FALLDET_REST_DELAY_MS       5000
#define FALLDET_IMPACT_DELAY_MS     500
#define FALLDET_DIFF_TRESHOLD       0.7

#define FALLDET_NORMAL              0
#define FALLDET_WAIT_IMPACT         1
#define FALLDET_WAIT_REST           2
#define FALLDET_COLLECT             3
#define FALLDET_CHECK               4
#define FALLDET_FALL_DETECTED       5

bool AceFallDetector::setup()
{
  prevMillis = millis();
  startMillis = millis();
  
  valueIdx = 0;
  valuesCntr = 0;
  avgNorm = 0;
  status = FALLDET_NORMAL;
  
  return true;
}

int AceFallDetector::addSample(float ax, float ay, float az, float gx, float gy, float gz)
{
  int currMillis = millis();
  int delta = currMillis - prevMillis;
  prevMillis = currMillis;

  // store and update data about initial position
  SENSOR_DATA* sd = &values[valueIdx];
  sd->ax = ax;
  sd->ay = ay;
  sd->az = az;
  sd->gx = gx / FALLDET_DEG_SCALE;
  sd->gy = gy / FALLDET_DEG_SCALE;
  sd->gz = gz / FALLDET_DEG_SCALE;
  valueIdx = (valueIdx + 1) % FALLDET_MONITOR_NUM_SAMPLES;

  valuesCntr ++;
  if (valuesCntr >= FALLDET_CALC_NUM_SAMPLES)
  {
    if (status == FALLDET_NORMAL)
    {
      // make an average to level out peaks
      computeAverage(values, valueIdx, FALLDET_MONITOR_NUM_SAMPLES, valuesCntr, &avg);
      Serial.print("Updating average... "); Serial.print(avg.ax); Serial.print(", "); Serial.print(avg.ay); Serial.print(", "); Serial.print(avg.az); Serial.println();
    }
    
    valuesCntr = 0;
  }

  // compute the norm and make an average of the last five values to level out peaks
  float aNorm = sqrt((ax*ax) + (ay*ay) + (az*az));
  avgNorm = averageNorm(aNorm);

  if (currMillis - startMillis < 1000)
  {
    // do nothing for the first second
    return false;
  }
  
  if ((status == FALLDET_NORMAL) || (status == FALLDET_WAIT_IMPACT))
  {
    // detect free fall
    if (avgNorm < FALLDET_FREEFALL_THRESHOLD)
    {
      // free fall detected: wait for impact
      Serial.print("Free fall detected "); Serial.print(avgNorm); Serial.print(" / "); Serial.print(FALLDET_FREEFALL_THRESHOLD); Serial.println();
      
      status = FALLDET_WAIT_IMPACT; 
      impactMillis = currMillis; 
    }

    // detect impact
    if (avgNorm > FALLDET_IMPACT_THRESHOLD)
    {
      // impact detected
      Serial.print("Impact detected "); Serial.print(avgNorm); Serial.print(" / "); Serial.print(FALLDET_IMPACT_THRESHOLD); Serial.println();

      status = FALLDET_WAIT_REST;  
      impactMillis = currMillis; 
    }

    if (status == FALLDET_WAIT_IMPACT)
    {
      // timeout to wait for impact after free fall. If timeout expires, return to normal condition
      if (currMillis - impactMillis > FALLDET_IMPACT_DELAY_MS)
      {
        status = FALLDET_NORMAL;
        valuesCntr = 0;
      }
    }
  }
  else if (status == FALLDET_WAIT_REST)
  {
    // wait 5 seconds to reach a "stable" position
    delta = currMillis - impactMillis;
    if (delta > FALLDET_REST_DELAY_MS)
    {
      // start collecting post-fall data
      Serial.println("Collecting post data..."); 
      status = FALLDET_COLLECT;
      postValuesCntr = 0;
    }
  }
  else if (status == FALLDET_COLLECT)
  {
    // collect post-impact data
    SENSOR_DATA* psd = &postValues[postValuesCntr];
    psd->ax = sd->ax;
    psd->ay = sd->ay;
    psd->az = sd->az;
    psd->gx = sd->gx;
    psd->gy = sd->gy;
    psd->gz = sd->gz;
    
    postValuesCntr ++;
    if (postValuesCntr >= FALLDET_POST_NUM_SAMPLES)
    {
      status = FALLDET_CHECK;
    }
  }
  else if (status == FALLDET_CHECK)
  {
    // check post-impact data
    SENSOR_DATA avgPostNorm;
    computeAverage(postValues, 0, FALLDET_POST_NUM_SAMPLES, FALLDET_POST_NUM_SAMPLES, &avgPostNorm);

    Serial.print("Checking position "); Serial.print(fabs(avgPostNorm.ax)); Serial.print("\t"); Serial.print(fabs(avgPostNorm.ay)); Serial.print("\t"); Serial.print(fabs(avgPostNorm.az)); Serial.println();
    Serial.print("Previous position "); Serial.print(fabs(avg.ax)); Serial.print("\t"); Serial.print(fabs(avg.ay)); Serial.print("\t"); Serial.print(fabs(avg.az)); Serial.println();
    Serial.print("Deltas            "); Serial.print(fabs(avgPostNorm.ax-avg.ax)); Serial.print("\t"); Serial.print(fabs(avgPostNorm.ay-avg.ay)); Serial.print("\t"); Serial.print(fabs(avgPostNorm.az-avg.az)); Serial.println();

    if ( (fabs(avgPostNorm.ax-avg.ax) > FALLDET_DIFF_TRESHOLD) || (fabs(avgPostNorm.ay-avg.ay) > FALLDET_DIFF_TRESHOLD) || (fabs(avgPostNorm.az-avg.az) > FALLDET_DIFF_TRESHOLD) )
    {
      // fall detected
      Serial.println("FALL DETECTED!!!");
      
      status = FALLDET_FALL_DETECTED;
      return true;
    }
    else
    {
      // this is not a fall, return to normal condition
      status = FALLDET_NORMAL;
      startMillis = currMillis;
      valuesCntr = 0;
    }
  }

  return false;
}

void AceFallDetector::computeAverage(SENSOR_DATA* values, int startIdx, int buffSize, int numSamples, SENSOR_DATA* avg)
{
  int idx = startIdx;

  avg->ax = 0;
  avg->ay = 0;
  avg->az = 0;
  avg->gx = 0;
  avg->gy = 0;
  avg->gz = 0;

  for (int i=0; i<numSamples; i++)
  {
    avg->ax += values[idx].ax;
    avg->ay += values[idx].ay;
    avg->az += values[idx].az;
    avg->gx += values[idx].gx;
    avg->gy += values[idx].gy;
    avg->gz += values[idx].gz;

    idx = (idx + 1) % buffSize;
  }

  avg->ax /= numSamples;
  avg->ay /= numSamples;
  avg->az /= numSamples;
  avg->gx /= numSamples;
  avg->gy /= numSamples;
  avg->gz /= numSamples;
}

float AceFallDetector::averageNorm(float n)
{
  return (avgNorm + ((n - avgNorm)/(float)FALLDET_AVERAGE_NUM_SAMPLES));
}
