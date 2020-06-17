
package com.WWU.CyberEnvironment.BLE;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import com.WWU.CyberEnvironment.BLE.authentication.CurrentUser;
import com.WWU.CyberEnvironment.BLE.authentication.LoginActivity;
import com.WWU.CyberEnvironment.BLE.repository.Repository;
import com.WWU.CyberEnvironment.BLE.repository.models.GetStationsResponseDto;
import com.WWU.CyberEnvironment.BLE.repository.models.StationDto;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        getStations();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if (bluetoothManager == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (ContextCompat.checkSelfPermission(DeviceScanActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DeviceScanActivity.this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_ENABLE_LOC);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                if (ContextCompat.checkSelfPermission(DeviceScanActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(DeviceScanActivity.this,
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            REQUEST_ENABLE_LOC);
                }
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case R.id.reg_device:
                final Dialog myDialog = new Dialog(this);
                myDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                myDialog.setContentView(R.layout.dialog_device_registration);
                myDialog.setCancelable(true);

                Button reg_device = myDialog.findViewById(R.id.button_device_register);

                //UI references
                mac_addr = myDialog.findViewById(R.id.input_mac_address);
                nickname = myDialog.findViewById(R.id.input_station_name);

                myDialog.show();
                Window window = myDialog.getWindow();
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

                reg_device.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (deviceAuthTask != null) {
                            return;
                        }
                        String tmpMac = mac_addr.getText().toString();
                        String tmpNick = nickname.getText().toString();

                        boolean cancel = false;
                        View focusView = null;

                        if (cancel) {
                            // There was an error; don't attempt dialog_account_registration and focus the first
                            // form field with an error.
                            focusView.requestFocus();
                        } else {
                            // Kick off a background task to perform the station dialog_account_registration attempt.
                            deviceAuthTask = new userDeviceTask(tmpMac, tmpNick, myDialog);
                            deviceAuthTask.execute((Void) null);
                        }
                    }
                });
                break;
            case R.id.sign_out:
                final Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                break;
        }
        return true;
    }

    /* Represents an asynchronous station dialog_account_registration task. */
    public class userDeviceTask extends AsyncTask<Void, Void, Boolean> {

        private final String macAddr;
        private final String nName;
        private final Dialog currDialog;

        userDeviceTask(String mac, String nick, Dialog newDialog) {
            macAddr = mac;
            nName = nick;
            currDialog = newDialog;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            String urlString = "http://64.146.143.236:8080/api/stations/";


            String auth_params = "{" +
                    "  \"station_name\":\"" + nName + "\"," +
                    "  \"mac_address\":\"" + macAddr + "\"," +
                    "  \"latitude\":\"" + "47.85" + "\"," +
                    "  \"longitude\":\"" + "-120.71" + "\"" +
                    "}";

            OutputStream out;

            InputStream inputStream;
            HttpURLConnection urlConnection;
            byte[] outputBytes;
            boolean status = true;

            //Context context;
            // Send post request to server
            try {
                URL url = new URL(urlString);

                urlConnection = (HttpURLConnection) url.openConnection();
                outputBytes = auth_params.getBytes("UTF-8");
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);
                urlConnection.setConnectTimeout(15000);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Authorization", "Token " + CurrentUser.key);
                urlConnection.connect();


                out = urlConnection.getOutputStream();
                out.write(outputBytes);
                out.flush();
                out.close();

                int responseCode = urlConnection.getResponseCode();

                if (responseCode >= 200 && responseCode < 400) {
                    inputStream = new BufferedInputStream(urlConnection.getInputStream());
                } else {
                    inputStream = new BufferedInputStream(urlConnection.getErrorStream());
                    status = false;

                }
                byte[] contents = new byte[1024];

                int bytesRead;
                ResponseData = "";
                while ((bytesRead = inputStream.read(contents)) != -1) {
                    ResponseData += new String(contents, 0, bytesRead);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return status;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            deviceAuthTask = null;

            if (success) {
                currDialog.dismiss();
                Toast.makeText(getApplicationContext(), R.string.reg_success,
                        Toast.LENGTH_LONG).show();
                getStations();
            } else {
                try {
                    //Check server validation errors for nickname
                    if (ResponseData.contains("station_name")) {
                        if (ResponseData.contains("characters")) {
                            nickname.setError("Nickname can be no longer than 20 characters");
                        } else if (ResponseData.contains("weather")) {
                            nickname.setError("Nickname is already in use");
                        } else if (ResponseData.contains("blank")) {
                            nickname.setError("Field is required");
                        } else {
                            nickname.setError("Error registering station");
                        }
                        nickname.requestFocus();
                    }

                    //Check server validation errors for mac address
                    if (ResponseData.contains("mac_address")) {
                        if (ResponseData.contains("format")) {
                            mac_addr.setError("Required in format 00:00:00:00:00:00");
                        } else if (ResponseData.contains("BLE")) {
                            mac_addr.setError("Station has already been registered");
                        } else if (ResponseData.contains("blank")) {
                            mac_addr.setError("Field is required");
                        } else {
                            mac_addr.setError("Error registering station");
                        }
                        mac_addr.requestFocus();
                    }

                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ENABLE_LOC: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    LinearLayout scanLayout = findViewById(R.id.scan_activity);
                    LayoutInflater inflater = (LayoutInflater)
                            getSystemService(LAYOUT_INFLATER_SERVICE);
                    View popupView = inflater.inflate(R.layout.popup_window, scanLayout);
                    int width = LinearLayout.LayoutParams.WRAP_CONTENT;
                    int height = LinearLayout.LayoutParams.WRAP_CONTENT;
                    boolean focusable = true; // lets taps outside the popup also dismiss it
                    final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

                    popupWindow.showAtLocation(popupView, Gravity.CENTER, 0, 0);

                    popupView.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            popupWindow.dismiss();
                            return true;
                        }
                    });
                }
                return;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

        if (stationAddrs.contains(device.getAddress())) {
            final Intent intent = new Intent(this, DeviceControlActivity.class);
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
            intent.putExtra("LOGIN_KEY", CurrentUser.key);
            intent.putExtra("STATION_ID", stationIds.get(stationAddrs.indexOf(device.getAddress())));

            if (mScanning) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mScanning = false;
            }
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.not_registered, Toast.LENGTH_LONG).show();
        }

    }

    private void getStations() {
        new Thread(new Runnable() {
            public void run() {
                GetStationsResponseDto response = repository.getStations();
                for (int i = 0; i < response.stations.size(); i++) {
                    StationDto station = response.stations.get(i);
                    stationAddrs.add(station.mac_address);
                    stationNames.add(station.station_name);
                    stationIds.add(station.id);
                }
            }
        }).start();
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {

                if (stationAddrs.contains(device.getAddress())) {
                    viewHolder.deviceName.setText(stationNames.get(stationAddrs.indexOf(device.getAddress())));
                } else {
                    viewHolder.deviceName.setText(deviceName);
                }
            }

            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getName() != null) {
                                mLeDeviceListAdapter.addDevice(device);
                                mLeDeviceListAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
            };

    static class ViewHolder {
        TextView deviceName;
    }

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LOC = 2;
    private static final long SCAN_PERIOD = 10000;

    private EditText mac_addr;
    private EditText nickname;

    private String ResponseData = "";

    ArrayList<String> stationAddrs = new ArrayList<>();
    ArrayList<String> stationNames = new ArrayList<>();
    ArrayList<Integer> stationIds = new ArrayList<>();

    private userDeviceTask deviceAuthTask = null;

    private Repository repository = new Repository();
}