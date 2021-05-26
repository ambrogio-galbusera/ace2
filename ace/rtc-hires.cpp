
#include "rtc-hires.h"

#define BASETIME_NANOSECONDS		30518

RTCHiRes::RTCHiRes()
{
  GCLK->GENDIV.reg = GCLK_GENDIV_ID(2)|GCLK_GENDIV_DIV(0);
  GCLK->GENCTRL.reg = (GCLK_GENCTRL_GENEN | GCLK_GENCTRL_SRC_OSCULP32K | GCLK_GENCTRL_ID(2));
  GCLK->CLKCTRL.reg = (uint32_t)((GCLK_CLKCTRL_CLKEN | GCLK_CLKCTRL_GEN_GCLK2 | (RTC_GCLK_ID << GCLK_CLKCTRL_ID_Pos)));
}

void RTCHiRes::microsleep(uint32_t micros)
{
  uint32_t preset = (micros * 1000) / 30500;
  preset = preset / 1000;
  
  // disable RTC
  RTC->MODE0.CTRL.reg &= ~RTC_MODE0_CTRL_ENABLE; 
  RTC->MODE0.CTRL.reg |= RTC_MODE0_CTRL_SWRST;
  
  // configure RTC in mode 0 (32-bit)
  RTC->MODE0.CTRL.reg |= RTC_MODE0_CTRL_PRESCALER_DIV1 | RTC_MODE0_CTRL_MODE_COUNT32;

  // initialize counter/compare values 
  RTC->MODE0.COUNT.reg = 0;
  RTC->MODE0.COMP[0].reg = preset;

  // enable the CMP0 interrupt in the RTC 
  RTC->MODE0.INTENSET.reg |= RTC_MODE0_INTENSET_CMP0;

  // enable RTC 
  RTC->MODE0.CTRL.reg |= RTC_MODE0_CTRL_ENABLE;
  
  SCB->SCR |= SCB_SCR_SLEEPDEEP_Msk;
  __DSB();
  __WFI();
}
