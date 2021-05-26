#ifndef __FALLDETECTOR__
#define __FALLDETECTOR__

#define FALLDET_MONITOR_DURATION_MS 1000
#define FALLDET_POST_DURATION_MS    5000
#define FALLDET_SAMPLE_TIME_MS      20
#define FALLDET_MONITOR_NUM_SAMPLES (FALLDET_MONITOR_DURATION_MS / FALLDET_SAMPLE_TIME_MS)
#define FALLDET_POST_NUM_SAMPLES    (FALLDET_POST_DURATION_MS / FALLDET_SAMPLE_TIME_MS)
#define FALLDET_CALC_NUM_SAMPLES    (FALLDET_MONITOR_DURATION_MS / FALLDET_SAMPLE_TIME_MS)
#define FALLDET_AVERAGE_TIME_MS     100
#define FALLDET_AVERAGE_NUM_SAMPLES (FALLDET_AVERAGE_TIME_MS / FALLDET_SAMPLE_TIME_MS)

typedef struct {
  float ax;
  float ay;
  float az;
  float gx;
  float gy;
  float gz;
} SENSOR_DATA;

class AceFallDetector {
public:
  AceFallDetector () {}
  
  // Empty destructor
  ~AceFallDetector() = default; 

  bool setup();
  int addSample(float x, float y, float z, float gx, float gy, float gz);

private:
  void computeAverage(SENSOR_DATA* values, int startIdx, int buffSize, int numSamples, SENSOR_DATA* avg);
  float averageNorm(float n);
  
  SENSOR_DATA values[FALLDET_MONITOR_NUM_SAMPLES];
  SENSOR_DATA avg;
  int valueIdx;
  int valuesCntr;

  SENSOR_DATA postValues[FALLDET_POST_NUM_SAMPLES];
  int postValuesCntr;

  float avgNorm;
  int status;

  int impactMillis;
  int prevMillis;
  int startMillis;
};

#endif
