package com.WWU.CyberEnvironment.BLE;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.WWU.CyberEnvironment.BLE.repository.EncryptionDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class DeviceControlActivity extends Activity {
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothLeService.connect(mDeviceAddress);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    //This broadcast receiver handles how the app handles notifications from the board
    //Of particular note is when the app receives ACTION_DATA_AVAILABLE, which is the signal that
    //the board is ready to send data.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                getGattCharacteristics();
                if(dataCharac != null) {
                    mBluetoothLeService.setCharacteristicNotification(dataCharac, true);
                    lightStatus = 1;
                    light.setBackgroundResource(R.drawable.orangecircle);
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                lightStatus = 3;
                light.setBackgroundResource(R.drawable.bluecircle);

                Log.d("Reception", "Starts Receiving Data");

                //Receives the whole symmetric key
                mBluetoothLeService.readCharacteristic(notifyCharac);
                byte[] read_status = notifyCharac.getValue();
                while(read_status[0] != 0x00){
                    read_status = notifyCharac.getValue();
                }
                mBluetoothLeService.readCharacteristic(dataCharac);
                byte[] keyPart = dataCharac.getValue();
                while(keyPart.length != 256) {
                    keyPart = dataCharac.getValue();
                }
                Log.d("Reception", "Received first part");
                //Receives the initialization vector (iv)
                mBluetoothLeService.readCharacteristic(notifyCharac);
                read_status = notifyCharac.getValue();
                while(read_status[0] != 0x00){
                    read_status = notifyCharac.getValue();
                }

                try{
                    Thread.sleep(delay);
                } catch (Exception InterruptedException){
                    Log.d("Waiting", "Failed to sleep");
                }

                Log.d("Reception", "Received Status before IV");
                mBluetoothLeService.readCharacteristic(dataCharac);
                byte[] iv = dataCharac.getValue();
                Log.d("Reception", "Length of read: "+iv.length);
                while(iv.length != 16){
                    Log.d("Reception", "Iteration of the while loop");
                    iv = dataCharac.getValue();
                }
                Log.d("Reception", "Received Second Part");
                //Create the SecretKey object in the encryption class to be used for decryption
                encryptionUnit.createSymmetricKey(keyPart, iv);
                //Recieve the number of 16 byte packets to recieve
                mBluetoothLeService.readCharacteristic(notifyCharac);
                read_status = notifyCharac.getValue();
                while(read_status[0] != 0x00){
                    read_status = notifyCharac.getValue();
                }

                try{
                    Thread.sleep(delay);
                } catch (Exception InterruptedException){
                    Log.d("Waiting", "Failed to sleep");
                }

                mBluetoothLeService.readCharacteristic(dataCharac);
                byte[] packetsRecv = dataCharac.getValue();
                while(packetsRecv.length != 4) {
                    packetsRecv = dataCharac.getValue();
                }
                byte[] init = Arrays.copyOfRange(packetsRecv, 0, 4);
                ByteBuffer bb_init = ByteBuffer.wrap(init);
                bb_init.order(LITTLE_ENDIAN);
                int packets = bb_init.getInt();

                final ArrayList<ArrayList<Float>> dataPoints = new ArrayList<>(packets);
                String sampleEnc = "";
                //Receive each packet of sensor readings
                for(int i = 0; i < packets; i++){
                    try{
                        Thread.sleep(delay);
                    } catch (Exception InterruptedException){
                        Log.d("Waiting", "Failed to sleep");
                    }
                    mBluetoothLeService.readCharacteristic(notifyCharac);
                    read_status = notifyCharac.getValue();
                    while(read_status[0] != 0x00){
                        read_status = notifyCharac.getValue();
                    }
                    try{
                        Thread.sleep(delay);
                    } catch (Exception InterruptedException){
                        Log.d("Waiting", "Failed to sleep");
                    }
                    mBluetoothLeService.readCharacteristic(dataCharac);
                    byte[] dataPointEnc = dataCharac.getValue();
                    while(dataPointEnc.length < 15){
                        dataPointEnc = dataCharac.getValue();
                    }
                    sampleEnc = dataPointEnc.toString();
                    encryptedData = sampleEnc;
                    byte[] dataPointDec = encryptionUnit.decrypt(dataPointEnc);

                    byte[] t_stamp = Arrays.copyOfRange(dataPointDec, 0, 4);
                    ByteBuffer bb = ByteBuffer.wrap(t_stamp);
                    bb.order(LITTLE_ENDIAN);
                    int real_t = bb.getInt();
                    int[] analog = new int[7];
                    int index = 0;
                    for(int j = 4; j < dataPointDec.length; j+=2){
                        //byte[] real_val = Arrays.copyOfRange(demoDecryption, i, i+2);
                        byte[] real_val = new byte[4];
                        real_val[0] = dataPointDec[j];
                        real_val[1] = dataPointDec[j+1];
                        real_val[2] = 0x00;
                        real_val[3] = 0x00;
                        bb = ByteBuffer.wrap(real_val);
                        bb.order(LITTLE_ENDIAN);
                        analog[index] = bb.getInt();
                        index++;
                    }

                    ArrayList<Float> entry = new ArrayList<>();
                    entry.add(new Float((float) real_t));
                    for(int j = 0; j < 7; j++){
                        entry.add(new Float((float) analog[j]));
                    }
                    dataPoints.add(entry);
		        }

		        incomingDataBuffer = dataPoints;

                //createJSONarray
                samples = convertToJSONArray(dataPoints);

                light.setBackgroundResource(R.drawable.yellowcircle);
            }

        }
    };

    /* Represents an asynchronous login/dialog_account_registration task used to authenticate
     * the user.
     */
    public class sendDatabase extends AsyncTask<Void, Void, Boolean> {

        private final JSONArray samples;

        sendDatabase(JSONArray newSamples) {
            samples = newSamples;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            String urlString = "http://64.146.143.236:8080/api/v2/DataSamples/";

            OutputStream out;

            InputStream inputStream;
            HttpURLConnection urlConnection;
            byte[] outputBytes;
            boolean status = true;

            try {
                URL url = new URL(urlString);

                urlConnection = (HttpURLConnection) url.openConnection();
                outputBytes = samples.toString().getBytes("UTF-8");
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);
                urlConnection.setConnectTimeout(15000);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Authorization", "Token " + key);
                urlConnection.connect();

                out = urlConnection.getOutputStream();
                out.write(outputBytes);
                out.flush();
                out.close();

                int responseCode = urlConnection.getResponseCode();
                Log.d(TAG, "Database response code = " + String.valueOf(responseCode));

                if(responseCode >= 200 && responseCode < 400) {
                    inputStream = new BufferedInputStream(urlConnection.getInputStream());
                } else {
                    inputStream = new BufferedInputStream(urlConnection.getErrorStream());
                    status = false;

                }
                byte[] contents = new byte[1024];

                int bytesRead;
                ResponseData = "";
                while((bytesRead = inputStream.read(contents)) != -1) {
                    ResponseData += new String(contents, 0, bytesRead);
                }

                Log.d("JSON Response = ", ResponseData);

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return status;
        }
        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            if (success) {
                lightStatus = 1;
                light.setBackgroundResource(R.drawable.greencircle);
            }
        }
    }

    private static JSONArray convertToJSONArray(ArrayList<ArrayList<Float>> data) {
        JSONArray sampleArray = new JSONArray();

        for (int i = data.size()-1; i > -1; i--) {
            for (int j = 0; j < data.get(i).size() - 1; j += 7) {
                JSONObject sample = new JSONObject();
                Log.d(TAG, "Returned timestamp = " + (long) data.get(i).get(j).floatValue());
                String date = createDate((long) data.get(i).get(j).floatValue());
                try {
                    sample.put("station", Integer.toString(stationId));
                    sample.put("collection_date",   date);
                    sample.put("depth_water_temp",  Float.toString(data.get(i).get(j + 1)));
                    sample.put("surf_water_temp",   Float.toString(data.get(i).get(j + 2)));
                    sample.put("air_temp",          Float.toString(data.get(i).get(j + 3)));
                    sample.put("salinity",          Float.toString(data.get(i).get(j + 4)));
                    sample.put("insolation",        Float.toString(data.get(i).get(j + 5)));
                    sample.put("turbidity",         Float.toString(data.get(i).get(j + 6)));
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException: " + e);
                }
                sampleArray.put(sample);
            }
        }
        return sampleArray;
    }

    private static String createDate(long timestamp) {
        Date date = new Date(timestamp * 1000);
        int year = date.getYear() + 1900;
        int month = date.getMonth() + 1;

        return String.format(
                Locale.US,
                "%4d-%02d-%02dT%02d:%02d:%02dZ",
                year,
                month,
                date.getDate(),
                date.getHours(),
                date.getMinutes(),
                date.getSeconds());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        key = intent.getStringExtra("LOGIN_KEY");
        stationId = intent.getIntExtra("STATION_ID", stationId);

        keyExist = false;
        incomingDataBuffer = null;
        samples = null;


        sharedPref = this.getSharedPreferences(
                getString(R.string.light_status_key), Context.MODE_PRIVATE);

        light = findViewById(R.id.circle);
        light.setBackgroundResource(R.drawable.orangecircle);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //Tell device to send incomingDataBuffer
        final Button updateValues = findViewById(R.id.dataButton);
        updateValues.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(keyExist) {
                    //Send the control signal to the board to request data transfer
                    byte[] msg = {0x01, 0x00};
                    notifyCharac.setValue(msg);
                    mBluetoothLeService.writeCharacteristic(notifyCharac);

                    //Send the board the public key from the key pair
                    byte[] pubKey = encryptionUnit.getPubKey();
                    try{
                        Thread.sleep(delay);
                    } catch (Exception InterruptedException){
                        Log.d("Waiting", "Failed to sleep");
                    }
                    dataCharac.setValue(pubKey);
                    mBluetoothLeService.writeCharacteristic(dataCharac);

                    Log.d("Decryption", "Sent Pub Key");
                    //After sending the control signal and public key, the app non-blocking waits for
                    //board to notify if that the data is ready to be sent, this gives the board time to
                    //encrypt the data to be sent. For next step in data reception see mGattUpdateReceiver
                } else {
                    Toast keyError = Toast.makeText(DeviceControlActivity.this, "You need to generate a key pair first.", Toast.LENGTH_LONG);
                    keyError.show();
                    light.setBackgroundResource(R.drawable.orangecircle);
                }
            }
        });

        //Start collection and send timestamp
        final Button start = findViewById(R.id.start);
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                lightStatus = 1;
                light.setBackgroundResource(R.drawable.greencircle);
                sendTime();
            }
        });

        //Generate Key Pair
        final Button keys = findViewById(R.id.generateKeyButton);
        keys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                encryptionUnit = new EncryptionUnit();

                TextView privKey = findViewById(R.id.privateKeyDisplay);
                String prKey = encryptionUnit.getPrivKeyS();
                privKey.setText("Private Key: " + prKey.substring(0, 60) + " ... " + prKey.substring(prKey.length()-60));

                TextView pubKey = findViewById(R.id.publicKeyDisplay);
                String pbKey = encryptionUnit.getPubKeyS();
                pubKey.setText("Public Key: " + pbKey.substring(0, 60) + " ... " + pbKey.substring(pbKey.length()-60));

                keyExist = true;
            }
        });

        //Show Decryption
        final Button display = findViewById(R.id.display_button);
        display.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(incomingDataBuffer == null){
                    Toast.makeText(display.getContext(), "You must download data first.", Toast.LENGTH_LONG).show();
                } else {

                  String encryptionString = encryptedData;


                    String decryptionString = "Temp1: " + incomingDataBuffer.get(0).get(1) +"\n" +
                            "Temp2: " + incomingDataBuffer.get(0).get(2) +"\n" +
                            "Temp2: " + incomingDataBuffer.get(0).get(3) +"\n" +
                            "Salinity: " + incomingDataBuffer.get(0).get(4) +"\n" +
                            "Insolation: " + incomingDataBuffer.get(0).get(5) +"\n" +
                            "Turbidity: " + incomingDataBuffer.get(0).get(6) +"\n";

                    final Dialog visualizationDialog = new Dialog(display.getContext());
                    visualizationDialog.setContentView(R.layout.dialog_decryption_display);

                    TextView encryptD = visualizationDialog.findViewById(R.id.encryption_preview);
                    TextView decryptD = visualizationDialog.findViewById(R.id.decryption_preview);

                    encryptD.setText(encryptionString);
                    decryptD.setText(decryptionString);

                    Button button = visualizationDialog.findViewById(R.id.close_decryption);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            visualizationDialog.dismiss();
                        }
                    });
                    visualizationDialog.show();
                }

            }

        });

        //Upload Data
        final Button upload = findViewById(R.id.upload);
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(samples == null) {
                    Toast.makeText(display.getContext(), "You must download data first.", Toast.LENGTH_LONG).show();
                } else {
                    if (mAuthTask != null) {
                        return;
                    }
                    //create DBobject
                    mAuthTask = new sendDatabase(samples);
                    //post to DB
                    mAuthTask.execute((Void) null);
                    samples = null;
                    incomingDataBuffer = null;
                    Toast.makeText(display.getContext(), "Data has been uploaded", Toast.LENGTH_SHORT).show();
                }

            }
        });

        //Find device status
        final Button status = findViewById(R.id.get_status);
        status.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                byte[] msg = {0x03, 0x00};
                notifyCharac.setValue(msg);
                mBluetoothLeService.writeCharacteristic(notifyCharac);

                mBluetoothLeService.readCharacteristic(notifyCharac);
                byte[] data = notifyCharac.getValue();
                if (data[0] == 0x03) {
                    lightStatus = 2;
                    light.setBackgroundResource(R.drawable.yellowcircle);
                } else if (data[0] == 0x04) {
                    light.setBackgroundResource(R.drawable.greencircle);
                } else {
                    light.setBackgroundResource(R.drawable.orangecircle);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(getString(R.string.light_status_key), lightStatus);
        editor.apply();
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private ArrayList<Float> convertValues(short[] values, int startingPoint) {
        int i = startingPoint;

        ArrayList<Float> convertedValues = new ArrayList<>();
        while(i < values.length){
            try {
                //Temps
                if (values[i] != 0 && values[i + 1] != 0 && values[i + 2] != 0 && values[i + 3] != 0) {
                    convertedValues.add(convertTemp(values[i]));
                    convertedValues.add(convertTemp(values[i + 1]));
                    convertedValues.add(convertTemp(values[i + 2]));

                    //Salinity
                    float salinity = convertSalinity(values[i + 3]);
                    if (salinity < 0) {
                        convertedValues.add(0F);
                    } else {
                        convertedValues.add(convertSalinity(values[i + 3]));
                    }
                }
            }catch (ArrayIndexOutOfBoundsException e){
                Log.d(TAG, "Array index out of bounds");
            }

            i = i + 4;
        }
        return convertedValues;
    }

    private float convertTemp(short t) {
        return ((((float) (t - 500) / 50) * 9) + 32);
    }
    private float convertSalinity(short s) {
        return (float) ((s - 1500) / 15);
    }
    private float convertTurbidity(short s) {
        return (float) s;
    }
    private float convertInsolation(short s) {
        return (float) s;
    }

    private void sendTime(){
        incomingDataBuffer = new ArrayList<>();
        Long currSeconds;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            TimeZone timeZone = TimeZone.getTimeZone("UTC");
            long date = Calendar.getInstance().getTime().getTime()/1000;
            currSeconds = (System.currentTimeMillis() - timeZone.getOffset(date))/1000;
        } else {
            currSeconds = System.currentTimeMillis()/1000;
        }

        Log.d(TAG, "Original  timestamp = " + currSeconds);
        byte[] flag = {0x02, 0x00};
        notifyCharac.setValue(flag);
        mBluetoothLeService.writeCharacteristic(notifyCharac);

        byte[] byteSeconds = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).order(LITTLE_ENDIAN).putLong(currSeconds).array();
        dataCharac.setValue(byteSeconds);
        mBluetoothLeService.writeCharacteristic(dataCharac);
    }

    private void getGattCharacteristics(){
        if (mBluetoothLeService != null) {
            getDataCharacteristics(mBluetoothLeService.getSupportedGattServices());

            for (BluetoothGattCharacteristic gattCharacteristic : mGattCharacteristics) {
                UUID id = gattCharacteristic.getUuid();

                if (id.toString().equals(data_UUID)) {
                    dataCharac = gattCharacteristic;

                } else if (id.toString().equals(notif_UUID)){
                    notifyCharac = gattCharacteristic;
                }
            }
        }
    }

    private void getDataCharacteristics(List<BluetoothGattService> gattServices){
        if (gattServices == null) return;
        mGattCharacteristics = new ArrayList<>();

        for (BluetoothGattService gattService : gattServices){
            if(gattService.getUuid().toString().equals(service_UUID)){

                mGattCharacteristics = gattService.getCharacteristics();
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private View light;
    private int lightStatus = 1;

    private int maxProgress = -1;

    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private List<BluetoothGattCharacteristic> mGattCharacteristics = new ArrayList<>();
    private boolean mConnected = false;
    BluetoothGattCharacteristic dataCharac;
    BluetoothGattCharacteristic notifyCharac;

    private String data_UUID = "0000abc1-0000-1000-8000-00805f9b34fb";
    private String notif_UUID = "0000abc0-0000-1000-8000-00805f9b34fb";
    private String service_UUID = "44332211-4433-2211-4433-221144332211";
    private String key = "";
    private String encryptedData;
    private String decryptedData;
    private static int stationId = 0;
    private String ResponseData ="";
    private sendDatabase mAuthTask = null;

    private EncryptionUnit encryptionUnit;
    private boolean keyExist;
    private byte[] incomingDataEncrypted;
    private byte[] incomingDataDecrypted;
    private TextView symKeyTextView;

    long delay = 250;

    ArrayList<ArrayList<Float>> incomingDataBuffer;
    JSONArray samples;

    SharedPreferences sharedPref;

    private ProgressBar progressBar;
}