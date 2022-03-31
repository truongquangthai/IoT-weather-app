package com.example.acer.lab_1_dashboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Region;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.SearchView;
import android.widget.ProgressBar;
import android.widget.ImageView;


import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.squareup.picasso.Picasso;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    String temp_querry_region = "Hanoi,VietNam";
    MQTTHelper mqttHelper;
    TextView txtTemp, txtHumid, txtWind, txtRegion_view;
    ToggleButton btnLED ;
    SearchView regionSearch;
    ProgressBar temp_bar, humi_bar, wind_bar;
    ImageView weather_icon;
    boolean send_message_again =false;
    int counter_fail_time=0;
    int waiting_period = 0;
    float x1, x2, y1, y2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        txtTemp = findViewById(R.id.txtTemp);
        txtHumid = findViewById(R.id.txtHumid);
        txtWind = findViewById(R.id.txtWind);
        txtRegion_view = findViewById(R.id.txtRegion_view);
        btnLED = findViewById(R.id.btnLED);
        regionSearch=findViewById(R.id.simpleSearchView);
        temp_bar = findViewById(R.id.temp_progressBar);
        humi_bar = findViewById(R.id.humi_progressBar);
        wind_bar = findViewById(R.id.wind_progressBar);
        weather_icon = findViewById(R.id.weather_icon);
        btnLED.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean ischecked) {
                if(ischecked == true){
                    Log.d("mqtt","Button is checked");
                    sendDataMQTT("quangthai/feeds/newfeed","1");
                }
                else {
                    Log.d("mqtt","Button is unchecked");
                    sendDataMQTT("quangthai/feeds/newfeed","0");
                    txtTemp.setText("OFF");
                    txtHumid.setText("OFF");
                    txtWind.setText("OFF");
                    temp_bar.setProgress(-15);
                    humi_bar.setProgress(0);
                    wind_bar.setProgress(0);
                    weather_icon.setVisibility(View.GONE);
                }
            }
        });
        regionSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if(query != ""){
                    sendDataMQTT("quangthai/feeds/region",query);
                    temp_querry_region = query;
                    return true;
                }
                else return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                String text = newText;
                return false;
            }
        });

        startMQTT();
        setupScheduler();
    }

    List<MQTTMessage>list=new ArrayList<>();

    private void setupScheduler(){
        Timer atimer=new Timer();
        TimerTask scheduler= new TimerTask() {
            @Override
            public void run() {
                if(waiting_period>0){
                    waiting_period--;
                    if(waiting_period==0)send_message_again=true;
                }
                if(send_message_again==true ){
                    if(counter_fail_time < 1){
                        sendDataMQTT(list.get(0).topic,list.get(0).mess);
                        list.remove(0);
                        counter_fail_time++;
                    }
                    else {
                        counter_fail_time=0;
                        send_message_again=false;
                        list.clear();
                    }
                }
            }
        };
        atimer.schedule(scheduler,5000,1000);
    }

    private void sendDataMQTT(String topic,String value)
    {
        waiting_period=2;
        send_message_again=false;
        MQTTMessage aMessage=new MQTTMessage();
        aMessage.topic=topic;aMessage.mess=value;
        list.add(aMessage);

        MqttMessage msg= new MqttMessage();
        msg.setId(124);
        msg.setQos(0); //quality of service
        msg.setRetained(true);

        String data=value;
        byte b[]=value.getBytes(Charset.defaultCharset().forName("UTF-8"));
        msg.setPayload(b);
        try{
            mqttHelper.mqttAndroidClient.publish(topic,msg);
            waiting_period = 0;

        }catch(MqttException e){

        }
    }

    private void startMQTT(){
        mqttHelper=new MQTTHelper(getApplicationContext(),"22112001");
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d("mqtt","connection is successful");
            }
            @Override
            public void connectionLost(Throwable cause) {
                Log.d("mqtt","disconnection ");
            }
            @Override
            public void messageArrived(String topic, final MqttMessage message) throws Exception {
                if(topic.equals("quangthai/feeds/newfeed")){
                    Log.d("mqtt","Received "+message.toString());

                    if((message.toString().equals("OFF") || message.toString().equals("0")) ){
                        btnLED.setChecked(false);
                        txtTemp.setText("OFF");
                        txtHumid.setText("OFF");
                        txtWind.setText("OFF");
                        temp_bar.setProgress(-20);
                        humi_bar.setProgress(0);
                        wind_bar.setProgress(0);
                        txtRegion_view.setText("WAITING....");
                        weather_icon.setVisibility(View.GONE);
                    }
                    else if((message.toString().equals("ON") || message.toString().equals("1") )  ){
                        btnLED.setChecked(true);
                        int comma_index =temp_querry_region.indexOf(',');
                        String output = temp_querry_region.substring(0,comma_index) + " - " + temp_querry_region.substring(comma_index + 1);
                        txtRegion_view.setText(output);
                    }
                }
                if(topic.equals("quangthai/feeds/weather-icon")){
                    String icon_url = "http://openweathermap.org/img/w/" + message.toString() + ".png";
                    weather_icon.setVisibility(View.VISIBLE);
                    Picasso.get().load(icon_url).into(weather_icon);
                }
                if(topic.equals("quangthai/feeds/bbc-temparature")){
                    txtTemp.setText(message.toString() + " °C");
                    int number = Integer.parseInt(message.toString());
                    try {
                        String numberBuffer = message.toString().substring(0,message.toString().indexOf('.'));
                        temp_bar.setProgress(Integer.valueOf(numberBuffer));
                    }
                    catch (Exception e){
                        temp_bar.setProgress(Integer.valueOf(message.toString()));
                    }
                }
                if(topic.equals("quangthai/feeds/bbc-humidity")){
                    txtHumid.setText(message.toString() + " %");
                    try {
                        String numberBuffer = message.toString().substring(0,message.toString().indexOf('.'));
                        humi_bar.setProgress(Integer.valueOf(numberBuffer));
                    }
                    catch (Exception e){
                        humi_bar.setProgress(Integer.valueOf(message.toString()));
                    }
                }
                if(topic.equals("quangthai/feeds/bbc-wind")){
                    txtWind.setText(message.toString() + " km/h");
                    try {
                        String numberBuffer = message.toString().substring(0,message.toString().indexOf('.'));
                        wind_bar.setProgress(Integer.valueOf(numberBuffer));
                    }
                    catch (Exception e){
                        wind_bar.setProgress(Integer.valueOf(message.toString()));
                    }
                }
                if(topic.equals("quangthai/feeds/region")){
                    if(message.toString().equals("ERROR REGION")){
                        txtRegion_view.setText("Error Region");
                    }
                    else if (message.toString().equals("CHANGE SUCCEED")) {
                        Log.d("mqtt","Received "+ message.toString());
                        int comma_index =temp_querry_region.indexOf(',');
                        String output = temp_querry_region.substring(0,comma_index) + " - " + temp_querry_region.substring(comma_index + 1);
                        txtRegion_view.setText(output);
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }
    public class MQTTMessage{
        public String topic;
        public String mess;
    }

}
