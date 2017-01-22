package yogurtdog.davishack70;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Object;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Color;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.geometry.SpatialReferences;
//import com.esri.android.map.ags.ArcGISDynamicMapServiceLayer;
//import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;


import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import android.util.Log;


public class MainActivity extends AppCompatActivity {
    private MapView mMapView;

    ArrayList<HashMap<String,String>> blgList;

    private static String url = "https://bldg-pi-api.ou.ad3.ucdavis.edu/piwebapi/search/query?q=afelementtemplate:building_ceed&count=130";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMapView = (MapView) findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, 38.5382, -121.7617,16);
        blgList = new ArrayList<>();
        mMapView.setMap(map);
        new GetData().execute();

        // Example of a call to a native method

    }

    private class GetData extends AsyncTask<Void,Void,Void>{

        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            Toast.makeText(MainActivity.this,"Json Data is downloading", Toast.LENGTH_LONG).show();
        }
        @Override
        protected  Void doInBackground(Void... arg0){
            HttpHandler sh = new HttpHandler();
            Authenticator.setDefault(new Authenticator(){
                protected PasswordAuthentication getPasswordAuthentication(){
                    return new PasswordAuthentication(" ou\\pi-api-public","M53$dx7,d3fP8".toCharArray());
                }
            });
            String jsonStr = sh.makeServiceCall(url);
            Log.e("GetData", "Obtained jsonStr" );

            //First Obtain building name, caan id, EUI Webid. Use caan to find location
            //EUI WebID to find usage
            if(jsonStr != null){
                try {
                    JSONObject jsonObj = new JSONObject(jsonStr);
                    JSONArray items = jsonObj.getJSONArray("Items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject d = items.getJSONObject(i);
                        String name = d.getString("Name");
                        Log.e("GetData", name );

                        //Assess attribute
                        String caan = null;
                        String euiID = null;
                        JSONArray attributes = d.getJSONArray("Attributes");
                        for(int k = 0; k<attributes.length();k++) {
                            JSONObject attribute = attributes.getJSONObject(k);
                            if (attribute.getString("Name").equals("CAAN")){
                                caan = attribute.getString("Value");
                                Log.e("GetData", "CAAN ID:" + caan );
                            }
                            if (attribute.getString("Name").equals("EUI")){
                                euiID = attribute.getString("WebId");
                                //Log.e("GetData", euiID);
                            }
                        }
                        //Find EUI
                        String euiURL = "https://bldg-pi-api.ou.ad3.ucdavis.edu/piwebapi/streams/" + euiID + "/value";
                        String euijsonStr = sh.makeServiceCall(euiURL);
                        JSONObject euijsonObj = new JSONObject(euijsonStr);

                        String euiVal = euijsonObj.getString("Value");
                        if(euiVal.matches(".*[A-Z].*") == true){
                            euiVal = "0";
                        }

                        Log.e("GetData", "EUI:" + euiVal);

                        //Find Location
                        String localeURL = "https://arm-tomcat1.ucdavis.edu/locations/buildings/thermalfeedback-1?apiKey=123";
                        String localejsonStr = sh.makeServiceCall(localeURL);
                        JSONArray localeArray = new JSONArray(localejsonStr);
                        String lat = null;
                        String longitude = null;
                        for(int p = 0; p<localeArray.length();p++) {
                            JSONObject tempLoc = localeArray.getJSONObject(p);
                            if (tempLoc.getString("assetNumber").equals(caan) && !caan.equals("0")){
                                lat = tempLoc.getString("latitude");
                                longitude = tempLoc.getString("longitude");
                                Log.e("GetData", "Lat,long : " + lat + "," + longitude );
                            }else if (caan.equals("0")){
                                lat = "0";
                                longitude = "0";
                                Log.e("GetData", "Lat,long : " + lat + "," + longitude );
                            }

                        }
                        HashMap<String,String> data = new HashMap<>();
                        data.put("Name",name);
                        data.put("EUI",euiVal);
                        data.put("Latitude",lat);
                        data.put("Longitude",longitude);

                        blgList.add(data);



                    }
                }catch(final JSONException e) {
                    Log.e("Parser", "Json parsing error: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    "Json parsing error: " + e.getMessage(),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }

                }else {
                    Log.e("Parser", "Couldn't get json from server.");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    "Couldn't get json from server. Check LogCat for possible errors!",
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

                }


            return null;


        }
        @Override
        protected void onPostExecute(Void result){
            Toast.makeText(getApplicationContext(),
                    "Finish Parsing!",
                    Toast.LENGTH_LONG)
                    .show();
            /*for(HashMap<String,String> map : blgList)
                for(Map.Entry<String, String> entry : map.entrySet()){
                    String j = entry.getValue();
                    Log.e("Post Execute", "Value:" + j );
                }*/
            //SpatialReference spr = SpatialReference.create(4326);
            int length =34;
            SimpleMarkerSymbol symbol[] = new SimpleMarkerSymbol[length];
            Point graphicPoint[] = new Point[length];
            Point pt1[] = new Point[length];
            Graphic graphic[] = new Graphic[length];
            GraphicsOverlay graphicsOverlay[] = new GraphicsOverlay[length];

            Log.e("WHEREISIT", "Name: " + blgList.get(35).get("Name ") +
                    " Long = " + blgList.get(35).get("Longitude") + " Lat = " + blgList.get(35).get("Longitude") + " EUI =  " + blgList.get(35).get("EUI") + "35");

            Log.e("WHEREISIT", "Name: " + blgList.get(36).get("Name ") +
                    " Long = " + blgList.get(36).get("Longitude") + " Lat = " + blgList.get(36).get("Longitude") + " EUI =  " + blgList.get(36).get("EUI") + "36");
            for(int i = 0; i < length;i++) {
                if(blgList.get(i).get("EUI") == null || blgList.get(i).get("Latitude") == null ||
                        blgList.get(i).get("Longitude") == null || blgList.get(i).get("Name") == null)
                    continue;

                String tempEUI = blgList.get(i).get("EUI");
                String tempLat = blgList.get(i).get("Latitude");
                String tempLong = blgList.get(i).get("Longitude");
                String tempName = blgList.get(i).get("Name");

                double euiVal = Double.parseDouble(tempEUI);
                double latVal = Double.parseDouble(tempLat);
                double longVal = Double.parseDouble(tempLong);
                //double x = longVal * 20037508.34 / 180;
                //double y = Math.log(Math.tan((90 + latVal) * Math.PI / 360)) / (Math.PI / 180);
                //y = y * 20037508.34 / 180;
                Log.e("WHEREISIT", "onPostExecute: " + tempName + longVal + ", " + latVal + "i = " + i);

                if (euiVal >= 150) {
                    euiVal = 150;
                }
                double colorRedD = (euiVal / 150) * 255;
                int colorRed = (int)colorRedD;
                symbol[i] = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.rgb(colorRed, 200, 0), 100);
                graphicPoint[i] = new Point(longVal, latVal, SpatialReferences.getWgs84());
                pt1[i] = (Point) GeometryEngine.project(graphicPoint[i], SpatialReferences.getWebMercator());
                graphic[i] = new Graphic(pt1[i], symbol[i]);
                graphicsOverlay[i] = new GraphicsOverlay();
                mMapView.getGraphicsOverlays().add(graphicsOverlay[i]);
                float opacity = 0.7f;
                graphicsOverlay[i].setOpacity(opacity);
                graphicsOverlay[i].getGraphics().add(graphic[i]);
            }


        }
    }
    @Override
    protected void onPause(){
        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mMapView.resume();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
