package app.jx.ex.sd;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.runtime.util.YailList;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import gnu.math.RealNum;

import org.json.JSONObject;

@DesignerComponent(
        version = 1,
        description = "An extension being able to generate images with SD model on the server",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "")

@SimpleObject(external = true)
//Libraries
@UsesLibraries(libraries = "")
//Permissions
@UsesPermissions(permissionNames = "")

public class StableDiffusionEx extends AndroidNonvisibleComponent {

    //Activity and Context
    private Context context;
    private Activity activity;

    public StableDiffusionEx(ComponentContainer container){
        super(container.$form());
        this.activity = container.$context();
        this.context = container.$context();
    }

    private static final String DEFAULT_SERVER_ADDRESS = "http://192.168.0.103:5000/";
    private String serverAddress = DEFAULT_SERVER_ADDRESS;

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "the address of the server.")
    public String ServerAddress() {
        return serverAddress;
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = DEFAULT_SERVER_ADDRESS)
    @SimpleProperty(description = "Set the address of the server.")
    public void ServerAddress(String addr) {
        serverAddress = addr;
    }

    // following are used for initializing

    private void initializeConection() {
        GetServerResponse("acc_test");
        PostServerResponse("acc_form", "v=0");
    }


    // following are the stuff used to handle the response from the server

    private String threadIdentifier;

    private int accumulatedCount = 0;

    private void GotResponse(String response) {
        String category = json_get(response, "type");
        if (category.equals("log")) {
            GotResponseForLog("log", json_get(response, "content"));
        } else if (category.equals("acc")) {
            accumulatedCount += 1;
            GotResponseForLog("acc", "accumulated for " + accumulatedCount + " times");
        } else if (category.equals("test")) {
            GotResponseForLog("test", response);
        } else if (category.equals("start")) {
            StartGenerating(response);
        } else if (category.equals("step")) {
            if (json_get(response, "status").equals("0")) {
                GenerationStepped(response);
            } else if (json_get(response, "status").equals("1")) {
                GenerationFinished();
            } else {
                GotResponseForLog("unknown status", response);
            }
        } else {
            GotResponseForLog("unknown type", response);
        }
    }




    public void GotResponseForLog(String type, int responseCode) {
        GotResponseForLog(type, "response code is " + responseCode);
    }


    @SimpleEvent(description = "Event triggered when the server response is used for log")
    public void GotResponseForLog(String type, String content) {
        EventDispatcher.dispatchEvent(this, "GotResponseForLog", type, content);
    }


    public void StartGenerating(String response) {
        threadIdentifier = json_get(response, "id");
        StartGenerating();
    }

    @SimpleEvent(description = "Event triggered when the server starts to generate")
    public void StartGenerating() {
        GetServerResponse("get_step/" + threadIdentifier);
        EventDispatcher.dispatchEvent(this, "StartGenerating");
    }


    public void GenerationStepped(String response) {
        String ImageUrl = serverAddress + "output/" + threadIdentifier;
        int step = Integer.parseInt(json_get(response, "step"));
        int sample = Integer.parseInt(json_get(response, "sample"));

        GenerationStepped(ImageUrl, step, sample);
    }

    @SimpleEvent(description = "Event triggered when the a denoised image is generated")
    public void GenerationStepped(String ImageUrl, int step, int sample) {
        GetServerResponse("get_step/" + threadIdentifier);
        EventDispatcher.dispatchEvent(this, "GenerationStepped", ImageUrl, step, sample);
    }


    public void GenerationFinished() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            GotResponseForLog("Exception", "Thread.sleep InterruptedException");
        }
        GenerationFinished(serverAddress + "output/" + threadIdentifier);
    }

    @SimpleEvent(description = "Event triggered when the generation is finished")
    public void GenerationFinished(String ImageUrl) {
        EventDispatcher.dispatchEvent(this, "GenerationFinished", ImageUrl);
    }




    // get the value of a json like a dic
    @SimpleFunction(description = "get the value of a json like a dic")
    public String json_get(String json_str, String key) {
        JSONObject jsonObject = new JSONObject(json_str);
        return jsonObject.getString(key);
    }



    // start generation!
    @SimpleFunction(description = "used to apply for generating an image with information provided")
    public void Generate(String prompt, int height, int width, int seed, int n_iter, int n_samples, int steps, int log_every_t) {
        
        initializeConection();
        
        height = boundBetween(height, 256, 1024);
        width = boundBetween(width, 256, 1024);
        seed = Math.abs(seed);
        n_iter = boundBetween(n_iter, 1, 3);
        n_samples = boundBetween(n_samples, 1, 3);
        steps = boundBetween(steps, 1, 100);
        log_every_t = boundBetween(log_every_t, 1, steps);

        String form = joinForm(prompt, height, width, seed, n_iter, n_samples, steps, log_every_t);
        PostServerResponse("run_model_from_html", form);
    }


    private int boundBetween(int target, int lower, int upper) {
        if (target < lower) {
            return lower;
        } else if (target > upper) {
            return upper;
        } else {
            return target;
        }
    }


    private String joinForm(String prompt, int height, int width, int seed, int n_iter, int n_samples, int steps, int log_every_t) {
        StringBuilder sb = new StringBuilder();
        sb.append("--prompt=").append(prompt).append("&")
          .append("--H=").append(height).append("&")
          .append("--W=").append(width).append("&")
          .append("--seed=").append(seed).append("&")
          .append("--n_iter=").append(n_iter).append("&")
          .append("--n_samples=").append(n_samples).append("&")
          .append("--steps=").append(steps).append("&")
          .append("--log_every_t=").append(log_every_t).append("&")
          .deleteCharAt(sb.length() - 1);
          return sb.toString();
    }


    @SimpleFunction(description = "Send a GET request to the server and get the response.")
    public void GetServerResponse(String behavior) {
        final String urlString = serverAddress + behavior;
        AsynchUtil.runAsynchronously(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder content = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                        in.close();
                        String response = content.toString();
                        GotResponse(response);
                    } else {
                        GotResponseForLog("Error", responseCode);
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    GotResponseForLog("Exception", e.getMessage());
                }
            }
        });
    }

    @SimpleFunction(description = "Send a POST request to the server with a form and get the response.")
    public void PostServerResponse(String behavior, String postData) {
        final String urlString = serverAddress + behavior;
        final String data = postData;

        AsynchUtil.runAsynchronously(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
                    wr.writeBytes(data);
                    wr.flush();
                    wr.close();

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder content = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                        in.close();
                        String response = content.toString();
                        GotResponse(response);
                    } else {
                        GotResponseForLog("Error", responseCode);
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    GotResponseForLog("Exception", e.getMessage());
                }
            }
        });
    }

    @SimpleEvent(description = "Event triggered when the server response is received for GET request.")
    public void GotGetResponse(String response) {
        EventDispatcher.dispatchEvent(this, "GotGetResponse", response);
    }

    @SimpleEvent(description = "Event triggered when the server response is received for POST request.")
    public void GotPostResponse(String response) {
        EventDispatcher.dispatchEvent(this, "GotPostResponse", response);
    }
}
