import requests
from tkinter import *
import serial.tools.list_ports
import math
import time
import sys
import paho.mqtt.client as mqttclient
old_city_name = "Hanoi,VietNam"
city_name = "Hanoi,VietNam"
openweather_api_key = "681a11a6815d261e966c108d8eed2d65"
feeds = ["quangthai/feeds/newfeed", "quangthai/feeds/bbc-humidity", "quangthai/feeds/bbc-temparature", "quangthai/feeds/bbc-wind", "quangthai/feeds/region", "quangthai/feeds/weather-icon"]

BUTTON_FEED_ID = "newfeed"
TEMPERATURE_FEED_ID = "bbc-temparature"
HUMIDITY_FEED_ID = "bbc-humidity"
WIND_FEED_ID = "quangthai/feeds/bbc-wind"
ADAFRUIT_IO_USERNAME = "quangthai"
ADAFRUIT_IO_KEY = "aio_TPxq65brYWk5NP51reOVI120gUW5"
broker = "io.adafruit.com"
mess = ""
startFlag = False
off_check = False

def get_weather(openweather_api_key, city):
    url = f"http://api.openweathermap.org/data/2.5/weather?q={city}&appid={openweather_api_key}"
    response = requests.get(url).json()
    temp = response['main']['temp']
    temp = math.floor(temp-273.15)
    humidity = response['main']['humidity']
    wind = response['wind']['speed']
    weather_icon = response['weather'][0]['icon']
    print(weather_icon)
    print(response)
    return {
        'temp': temp,
        'humidity': humidity,
        'wind': wind,
        'weather_icon': weather_icon
    }
try:
    weather = get_weather(openweather_api_key, city_name)
    old_city_name = city_name
except KeyError:
    weather = get_weather(openweather_api_key, old_city_name)
    city_name = old_city_name


def connect(client, userdata, flags, rc):
    if rc == 0:
            for f in feeds:
                client.subscribe(f)
                print("Ket noi toi" + f)
            #print("Ket noi thanh cong..."+str(rc))
    else:
        print("ket noi khong thanh cong... loi=" + str(rc))


def  subscribe(client, userdata, topic, granted_qos):
    print("Subscribe thanh cong...")


def disconnected(client, userdata, flags, rc=0):
    print("Ngat ket noi...")
    sys.exit(1)


def  message(client, userdata, message):
    global startFlag
    global city_name
    global weather
    global old_city_name
    global off_check
    print("Nhan du lieu: " + message.payload.decode('UTF + 8') + " from " +  message.topic)
    time.sleep(1)
    message_receive = str(message.payload.decode('UTF + 8'))
    if message_receive == "1":
        startFlag = True
        off_check = False
    elif message_receive == "0":
        startFlag = False
        if off_check:
            client.publish("quangthai/feeds/bbc-temparature", -15)
            client.publish("quangthai/feeds/bbc-humidity", 0)
            client.publish("quangthai/feeds/bbc-wind", 0)
            client.publish("quangthai/feeds/region", "SYSTEM PAUSE")
            off_check = True

    if message.topic == "quangthai/feeds/region" and message_receive.find(",") != -1 and message_receive != city_name:
        city_name = message_receive


def getPort():
    ports = serial.tools.list_ports.comports()
    N = len(ports)
    commPort = "None"
    for i in range(0, N):
        port = ports[i]
        strPort = str(port)
        if "com0com" in strPort:
            splitPort = strPort.split(" ")
            commPort = (splitPort[0])
    print(commPort)
    return commPort

#ser = serial.Serial( port=getPort(), baudrate=115200)

isMicrobitConnected = False
if(getPort() != "None"):
    ser = serial.Serial(port=getPort(), baudrate=115200)
    isMicrobitConnected = False


def processData(data):
    data = data.replace("!", "")
    data = data.replace("#", "")
    splitData = data.split(":")
    print(data)
    print(splitData)
    if splitData[0] == "BUTTON":
        client.publish("quangthai/feeds/newfeed", splitData[1])
    if startFlag:
        if splitData[0] == "TEMP":
            client.publish("quangthai/feeds/bbc-temparature", splitData[1])
        elif splitData[0] == "HUMI":
            client.publish("quangthai/feeds/bbc-humidity", splitData[1])
        elif splitData[0] == "WIND":
            client.publish("quangthai/feeds/bbc-wind", splitData[1])
    else:
        print("turn button on to receive data")
        time.sleep(5)


def readSerial():
    bytesToRead = ser.inWaiting()
    if (bytesToRead > 0):
        global mess
        mess = mess + ser.read(bytesToRead).decode("UTF-8")
        while ("#" in mess) and ("!" in mess):
            start = mess.find("!")
            end = mess.find("#")
            processData(mess[start:end + 1])
            if (end == len(mess)):
                mess = ""
            else:
                mess = mess[end+1:]


client = mqttclient.Client()
client.username_pw_set(ADAFRUIT_IO_USERNAME, ADAFRUIT_IO_KEY)
client.on_connect = connect
client.on_message = message
client.on_subscribe = subscribe
client.on_disconnect = disconnected

print("connecting to broker")
client.connect(broker)
client.loop_start()
client.publish("quangthai/feeds/bbc-temparature", -20)
client.publish("quangthai/feeds/bbc-humidity", 0)
client.publish("quangthai/feeds/bbc-wind", 0)
while True:
     if startFlag:
         if city_name != old_city_name:
             try:
                 weather = get_weather(openweather_api_key, city_name)
                 old_city_name = city_name
                 client.publish("quangthai/feeds/region", "CHANGE SUCCEED")
                 client.publish("quangthai/feeds/bbc-temparature", weather['temp'])
                 client.publish("quangthai/feeds/bbc-humidity", weather['humidity'])
                 client.publish("quangthai/feeds/bbc-wind", weather['wind'])
                 client.publish("quangthai/feeds/weather-icon", weather['weather_icon'])
             except KeyError:
                 city_name = old_city_name
                 client.publish("quangthai/feeds/region", "ERROR REGION")
                 time.sleep(3)
                 weather = get_weather(openweather_api_key, city_name)
                 client.publish("quangthai/feeds/region", city_name)

         else:
            print("Cap nhat nhiet do: ", weather['temp'])
            print("Cap nhat do am: ", weather['humidity'])
            client.publish("quangthai/feeds/bbc-temparature", weather['temp'])
            client.publish("quangthai/feeds/bbc-humidity", weather['humidity'])
            client.publish("quangthai/feeds/bbc-wind", weather['wind'])
            client.publish("quangthai/feeds/weather-icon", weather['weather_icon'])
         time.sleep(10)
     else:
            print("waiting to turn on")
            time.sleep(10)