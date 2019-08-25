#define double_buffer

#include <Arduino.h>
#include <WiFi.h>
#include <PxMatrix.h>
#include <WiFiClient.h>
#include <Queue.h>

#define BUTTON_PIN GPIO_NUM_27
long lastPress = 0;
volatile bool buttonPressed = false;

#define MATRIX_WIDTH 64
#define MATRIX_HEIGHT 32
#define P_LAT 22
#define P_A 19
#define P_B 23
#define P_C 18
#define P_D 5
#define P_E 15
#define P_OE 4

hw_timer_t* timer = NULL;
portMUX_TYPE timerMux = portMUX_INITIALIZER_UNLOCKED;
uint8_t display_draw_time=10; //10-50 is usually fine
PxMATRIX display(MATRIX_WIDTH, MATRIX_HEIGHT, P_LAT, P_OE, P_A, P_B, P_C, P_D, P_E);
#define POWER_PIN GPIO_NUM_32
const uint16_t BLUE = display.color565(0,0,255);

char ssid[] = //SSID;
char pass[] = //PASSWORD;

#define FRAME_COUNT 27
#define PACKET_SIZE 4104

void IRAM_ATTR display_updater(){
  portENTER_CRITICAL_ISR(&timerMux);
  display.display(display_draw_time);
  portEXIT_CRITICAL_ISR(&timerMux);
}

void display_update_enable(bool is_enable) {
  if (is_enable) {
    timer = timerBegin(0, 80, true);
    timerAttachInterrupt(timer, &display_updater, true);
    timerAlarmWrite(timer, 2000, true);
    timerAlarmEnable(timer);
  } else {
    timerDetachInterrupt(timer);
    timerAlarmDisable(timer);
  }
}

WiFiServer server(41382);
TaskHandle_t RenderTask;
TaskHandle_t ReceiveTask;

//DMA_ATTR uint8_t frames[PACKET_SIZE * FRAME_COUNT];
uint8_t * frames;
Queue<int> queue(FRAME_COUNT);

long currentFrame = 0;

#define HEADER_SIZE 8

uint16_t drawImage(uint8_t * newData, size_t size) {
  display.clearDisplay();
  uint16_t id = ((uint16_t)(newData[0]) << 8) | (uint16_t)newData[1];
  uint16_t delay = ((uint16_t)(newData[2]) << 8) | (uint16_t)newData[3];
  uint16_t width = ((uint16_t)(newData[4]) << 8) | (uint16_t)newData[5];
  uint16_t height = ((uint16_t)(newData[6]) << 8) | (uint16_t)newData[7];

  // Serial.printf("ID[%d] res[%d,%d] size[%d]\n", id, width, height, size);

  for (int i = HEADER_SIZE; i < size; i = i + 2) {
    int pos = (i - HEADER_SIZE + 1) / 2;
    const int x = pos % height;
    const int y = floor(pos / max((uint16_t)1,height));

    display.drawPixelRGB565(y, x, ((uint16_t)(newData[i]) << 8) | (uint16_t)newData[i+1]);
  }
  return delay;
}

int fps = 0;
long previousMillis = 0;

void outputMatrix(void * parameter){
  while (true) {
    if (queue.count() > 5) {
      int offset = queue.pop();
      fps++;
      drawImage(&frames[offset], PACKET_SIZE);
      display.showBuffer();
    } 

    unsigned long currentMillis = millis();
    if (currentMillis - previousMillis >= 1000) {
      previousMillis = currentMillis;
      if (fps > 0) {
        Serial.printf("FPS: %d, Buffer: %d\n\r",fps,queue.count());
      }
      fps = 0;
    }
    delay(20); 
  }
}

WiFiClient client;

void receive(void * parameter){
  while (true) {
    if (client) {

      size_t size = client.available();
      if(size && queue.count() < FRAME_COUNT) {
        int offset = (currentFrame % FRAME_COUNT) * PACKET_SIZE;
        client.readBytes(&frames[offset], PACKET_SIZE);
        queue.push(offset);
        //Serial.printf("buffer: %d\n\r",queue.count());
      }
    } else {
      client = server.available();
    }
    delay(10);
  }
}

void IRAM_ATTR ButtonPressed() {
  if (millis() > ( lastPress + 1000)) {
    buttonPressed = true;
    lastPress = millis();
  }
}

void setup() {
  Serial.begin(115200);
  Serial.println("Start!");
  pinMode(BUTTON_PIN, INPUT);
  
  Serial.print("Connecting");
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, pass);
  int c = 0;
  while (WiFi.waitForConnectResult() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    if (c++ > 2) {
      ESP.restart();
    }
  }

  Serial.println(WiFi.localIP());

  pinMode(POWER_PIN, OUTPUT);
  digitalWrite(POWER_PIN, HIGH);

  Serial.println(sizeof(uint8_t));
 
  Serial.println(xPortGetFreeHeapSize());
  Serial.println(ESP.getFreeHeap());
  frames = (uint8_t*) heap_caps_calloc(FRAME_COUNT,PACKET_SIZE, MALLOC_CAP_8BIT);
  if (frames == NULL) {
    Serial.println("out of memory");
  }
  Serial.println(ESP.getFreeHeap());

  delay(500);

  display.begin(16);
  display_update_enable(true); 
  display.flushDisplay();
  display.setTextWrap(false);   

  display.fillScreen(BLUE);
  display.showBuffer();

  xTaskCreatePinnedToCore(
    outputMatrix,   /* Task function. */
    "outputMatrix", /* name of task. */
    5000,           /* Stack size of task */
    NULL,           /* parameter of the task */
    2,              /* priority of the task */
    &RenderTask,    /* Task handle to keep track of created task */
    1); 

  xTaskCreatePinnedToCore(
    receive,        /* Task function. */
    "receive",      /* name of task. */
    5000,           /* Stack size of task */
    NULL,           /* parameter of the task */
    2,              /* priority of the task */
    &ReceiveTask,   /* Task handle to keep track of created task */
    0); 

  attachInterrupt(BUTTON_PIN, ButtonPressed, HIGH);

  server.begin();
}

uint8_t read8(WiFiClient client) {
  return ((uint16_t)(client.read()) << 8) | (uint16_t)client.read();
}

uint16_t read16(uint8_t * data, int pos) {
  return ((uint16_t)(data[(pos*2)]) << 8) | (uint16_t)data[(pos*2)+1];
}



int loops = 0;
long prevTime = 0;

void loop() {

}
