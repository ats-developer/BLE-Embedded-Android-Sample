package com.atharvasystem.bleembeddedsample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.atharvasystem.bleembeddedsample.Service.BluetoothLeService;
import com.atharvasystem.bleembeddedsample.Utility.Constants;
import com.atharvasystem.bleembeddedsample.Utility.SampleGattAttributes;

import java.util.List;

public class BleActivity extends AppCompatActivity {

    private Context mContext;
    private TextView tvConnect, tvSendCommand, tvReceivedData;
    private EditText etCommand;
    private Button btnSend;

    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;
    private final static String TAG = BleActivity.class.getSimpleName();

    private String macAddress;

    private AlertDialog alertDialog, alertDialogBluetooth;
    private static final int REQUEST_ENABLE_BT = 2;
    private String strReceived = "";
    private InputMethodManager imm;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, getString(R.string.unableInitialize));
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(macAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                enableBluetooth();      //check bluetooth is connected or not
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                try {
                    displayGattServices(mBluetoothLeService.getSupportedGattServices());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));                  //print received data string
            }
        }
    };

    private void enableBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(mContext, getString(R.string.msg_bluetooth_not_available), Toast.LENGTH_LONG).show();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {           //check phone bluetooth is on or off
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {                                        //device bluetooth is not connected
            tvConnect.setText(getString(R.string.disconnected));
            showAlertConnectDevice(getString(R.string.msg_connect_device));
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        mContext = BleActivity.this;

        tvConnect = findViewById(R.id.text_connect);
        etCommand = findViewById(R.id.edit_command);
        btnSend = findViewById(R.id.button_send);
        tvSendCommand = findViewById(R.id.text_send_command);
        tvReceivedData = findViewById(R.id.text_received_data);

        final Intent intent = getIntent();
        macAddress = intent.getStringExtra(Constants.EXTRAS_DEVICE_ADDRESS);

        Intent gattServiceIntent = new Intent(mContext, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);                       //bind bluetooth service

        imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBluetoothLeService != null && mConnected) {
                    if (etCommand.getText().toString().trim().length() > 0) {
                        sendMessage(etCommand.getText().toString());
                    }
                }
            }
        });
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(macAddress);
            Log.d(TAG, getString(R.string.connectResult) + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        System.gc();
        unregisterReceiver(mGattUpdateReceiver);
    }

    private void displayData(String data) {
        if (data != null) {
            Log.d(getString(R.string.Received), data);
            strReceived = data + "\n" + strReceived;
            tvReceivedData.setText(strReceived);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid;
        String unknownServiceString = getResources().getString(R.string.unknown_service);

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);

            // If the service exists for Your UUID Serial, say so.
            if (SampleGattAttributes.lookup(uuid, unknownServiceString).equals(Constants.EXTRAS_YOUR_UUID)) {
                tvConnect.setText(getString(R.string.connected));
            }
        }

    }

    //Bluetooth service actions to perform
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void sendMessage(String message) {
        Log.d(getString(R.string.Sent), message);
        try {
            final byte[] tx = message.getBytes();   //convert string to byte and send to device
            if (mConnected) {
                if (characteristicTX != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            characteristicTX.setValue(tx);
                            mBluetoothLeService.writeCharacteristic(characteristicTX);
                            mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
                            String strCommand = getString(R.string.Sent) + "\t \t" + etCommand.getText().toString();
                            tvSendCommand.setText(strCommand);
                            imm.hideSoftInputFromWindow(btnSend.getWindowToken(), 0);
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            //phone bluetooth enable request
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode != Activity.RESULT_OK) {
                    // User did not enable Bluetooth or an error occurred
                    showAlertBluetooth();
                }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void showAlertBluetooth() {
        try {
            alertDialogBluetooth = new AlertDialog.Builder(mContext)
                    //set icon
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    //set title
                    .setTitle(getString(R.string.bt_not_enable))
                    //set message
                    .setMessage(getString(R.string.bt_enable_msg))
                    //set positive button
                    .setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                        }
                    })
                    .setNegativeButton(getString(R.string.close_device), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            alertDialogBluetooth.cancel();
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlertConnectDevice(String msg) {
        try {
            alertDialog = new AlertDialog.Builder(mContext)
                    //set icon
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    //set title
                    .setTitle(getString(R.string.title_device_not_work))
                    //set message
                    .setMessage(msg)
                    //set positive button
                    .setPositiveButton(getString(R.string.connect), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            tvConnect.setText(getString(R.string.title_connecting));
                            mBluetoothLeService.connect(macAddress);                                // connect with bluetooth device again
                        }
                    })
                    .setNegativeButton(getString(R.string.close_device), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //set what would happen when positive button is clicked
                            alertDialog.cancel();
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
