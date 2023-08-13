package com.simpleprintdemo.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.example.tscdll.TSCActivity;
import com.example.tscdll.TSCUSBActivity;
import com.simpleprintdemo.*;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.simpleprintdemo.util.DemoSleeper;
import com.simpleprintdemo.broadcast.DeviceReceiver;
import com.simpleprintdemo.util.SettingsHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;

public class MainActivity extends AppCompatActivity {

    BluetoothConnection thePrinterConn, thePrinterUSB;
    public static final int ENABLE_BLUETOOTH = 1;
    private RadioButton btRadioButton;
    private EditText macAddressEditText;
    private static final String PREFS_NAME = "OurSavedAddress";
    private Button testButton;
    private TextView statusField;
    private static final int PERMISSION_REQUEST_CODE = 200;

    //______________________________ for Bluetooth start

    View dialogView;
    BluetoothAdapter bluetoothAdapter;
    ArrayAdapter<String> adapter1, adapter2;
    private ListView lv1, lv2;
    ArrayList<String> deviceList_bonded = new ArrayList<>();
    ArrayList<String> deviceList_found = new ArrayList<>();
    private Button btn_scan;
    private LinearLayout LLlayout;
    AlertDialog dialog;
    String mac;
    public DeviceReceiver myDevice;
    TSCActivity TscDll;

    //_________________________ for USB start

    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbInterface mInterface;
    UsbEndpoint mEndPoint;
    PendingIntent mPermissionIntent;
    HashMap<String, UsbDevice> mDeviceList;
    Iterator<UsbDevice> mDeviceIterator;
    private static final String ACTION_USB_PERMISSION = "com.zebra.connectivitydemo.USB_PERMISSION";
    static Boolean forceCLaim = true;
    String strType = "";
    TSCUSBActivity TscUSB;
    UsbDevice device;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        TscUSB = new TSCUSBActivity();
        TscDll = new TSCActivity();
        macAddressEditText = this.findViewById(R.id.macInput);

        statusField = this.findViewById(R.id.statusText);
        btRadioButton = this.findViewById(R.id.bluetoothRadio);

        if (!checkPermission()) {
            requestPermission();
        }
        //usbInit();
        setBluetooth();
        strType = "Bluetooth";
        RadioGroup radioGroup = this.findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.bluetoothRadio) {
                toggleEditField(macAddressEditText, true);
                setBluetooth();
                strType = "Bluetooth";
            } else {
                toggleEditField(macAddressEditText, false);
                usbInit();
            }
        });

        testButton = this.findViewById(R.id.testButton);
        testButton.setOnClickListener(v -> {
            if (strType.isEmpty()) {
                Toast.makeText(getApplicationContext(), "Please Select Print Option...", Toast.LENGTH_LONG).show();
            } else if (strType.equals("Bluetooth")) {
                printWithBluetooth();
            } else if (strType.equals("USB")) {
                printWithUSB();
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void setStatus(final String statusMessage, final int color) {
        runOnUiThread(() -> {
            statusField.setBackgroundColor(color);
            statusField.setText(statusMessage);
        });
        DemoSleeper.sleep(1000);
    }

    private void enableTestButton(final boolean enabled) {
        runOnUiThread(new Runnable() {
            public void run() {
                testButton.setEnabled(enabled);
            }
        });
    }

    private void doConnectionTest() {
        setStatus("Connecting...", Color.YELLOW);
        thePrinterConn = null;
        if (isBluetoothSelected()) {
            thePrinterConn = new BluetoothConnection(getMacAddressFieldText());
            SettingsHelper.saveBluetoothAddress(this, getMacAddressFieldText());
            try {
                thePrinterConn.open();
                setStatus("Connected", Color.GREEN);
                sendZplOverBluetooth();
            } catch (ConnectionException e) {
                setStatus("Comm Error! Disconnecting", Color.RED);
                DemoSleeper.sleep(1000);
            }
        } else {
            thePrinterConn = new BluetoothConnection(getMacAddressFieldText());
            try {
                thePrinterConn.open();
                printUSB(mConnection, mInterface);
            } catch (ConnectionException e) {
                setStatus("Comm Error! Disconnecting", Color.RED);
                DemoSleeper.sleep(1000);
            }
        }
    }

    private void sendZplOverBluetooth() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    ZebraPrinter zPrinterIns = ZebraPrinterFactory.getInstance(thePrinterConn);
                    zPrinterIns.sendCommand("! U1 setvar \"device.languages\" \"zpl\"");
                    zPrinterIns.sendCommand("~jc^xa^MNN^LL450^xz^xa^jus^xz");
                    //Thread.sleep(500);
                    //zPrinterIns.sendCommand("^XA^CF0,30^FO200,50^FDSSKC Ovwell^FS^CF0,30^FO210,90^FDVisitor Pass^FS^FO40,150^GB460,1,1^FS^CFA,30^FO50,190^FDName    :Sachin Pangare^FS^FO50,230^FDCompany :Appstek ^FS^FO50,270^FDVehicle :AP09BU8621^FS^FO50,310^FDDate    :14-04-2022^FS^FO50,350^FDTime    :1:01 PM^FS^CFA,15^XZ"); //---- sample 2
                    //zPrinterIns.sendCommand("^XA^CF0,30^FO250,50^FDSSKC Orwell Bluetooth^FS^CF0,30^FO250,90^FDVisitor Pass^FS^FO90,150^GB460,1,1^FS^CFA,30^FO120,190^FDName    :Sachin Pangare^FS^FO120,230^FDCompany :Appstek ^FS^FO120,270^FDVehicle :AP09BU8621^FS^FO120,310^FDDate    :14-04-2022^FS^FO120,350^FDTime    :1:01 PM^FS^CFA,15^XZ"); //---- sample 2
                    zPrinterIns.sendCommand("^XA^CF0,30^FO250,50^FDSSKC " + "Tower" + "^FS^CF0,30^FO250,90^FDVisitor Pass^FS^FO90,150^GB460,1,1^FS^CFA,30^FO120,190^FDName    :" + "ABC" + "^FS^FO120,230^FDCompany :" + "XYZ" + "^FS^FO120,270^FDVehicle :" + "FD67DR987" + "^FS^FO120,310^FDDate    :25-04-2022^FS^FO120,350^FDTime    :1:25 PM^FS^CFA,15^XZ"); //---- sample 2
                    // Thread.sleep(500);
                    // thePrinterConn.close();
                } catch (Exception e) {
                    setStatus("Exception" + e.getMessage(), Color.RED);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void printWithUSB() {

        printUSB(mConnection, mInterface);
    }

    private void doUSBConnectionTest() {
        setStatus("Connecting...", Color.YELLOW);
        thePrinterUSB = null;
        thePrinterUSB = new BluetoothConnection(getMacAddressFieldText());
        try {
            thePrinterUSB.open();
            printUSB(mConnection, mInterface);
        } catch (ConnectionException e) {
            setStatus("Comm Error! Disconnecting", Color.RED);
            DemoSleeper.sleep(1000);
        }
    }

    private void printUSB(final UsbDeviceConnection connection, final UsbInterface uInterface) {
        if (uInterface == null) {
            Toast.makeText(getApplicationContext(), "INTERFACE IS NULL", Toast.LENGTH_SHORT).show();
        } else if (connection == null) {
            Toast.makeText(getApplicationContext(), "CONNECTION IS NULL", Toast.LENGTH_SHORT).show();
        } else if (forceCLaim == null) {
            Toast.makeText(getApplicationContext(), "FORCE CLAIM IS NULL", Toast.LENGTH_SHORT).show();
        } else {
            connection.claimInterface(uInterface, forceCLaim);
            TscUSB.openport(mUsbManager, device);
            //Set label width, height, printing speed, printing density, sensor type, gap/black mark vertical spacing, gap/black mark offset distance)
            TscUSB.setup(70, 50, 4, 4, 0, 0, 0);
            TscUSB.clearbuffer();
            TscUSB.printerfont(120, 10, "4", 0, 1, 1, "SSKC " + "Tower");
            TscUSB.printerfont(130, 50, "4", 0, 1, 1, "Visitor Pass ");
            TscUSB.printerfont(10, 70, "3", 0, 1, 1, "____________________________________");
            TscUSB.printerfont(20, 120, "3", 0, 1, 1, "Name     :" + "ABC");
            TscUSB.printerfont(20, 160, "3", 0, 1, 1, "Company  :" + "XYZ");
            TscUSB.printerfont(20, 200, "3", 0, 1, 1, "Vehicle  :" + "MH56FR0987");
            TscUSB.printerfont(20, 240, "3", 0, 1, 1, "Date     :" + "26-04-2022");
            TscUSB.printerfont(20, 280, "3", 0, 1, 1, "Time     :" + "05-21-PM");
            TscUSB.printlabel(1, 1);
            TscUSB.closeport(500);

            // sendZplOverUSB();

        }
    }

    private void sendZplOverUSB() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    ZebraPrinter zPrinterIns = ZebraPrinterFactory.getInstance(thePrinterUSB);
                    zPrinterIns.sendCommand("! U1 setvar \"device.languages\" \"zpl\"");
                    zPrinterIns.sendCommand("~jc^xa^jus^xz");
                    // Thread.sleep(500);
                    zPrinterIns.sendCommand("^XA^CF0,30^FO250,50^FDSSKC Orwell USB^FS^CF0,30^FO250,90^FDVisitor Pass^FS^FO90,150^GB460,1,1^FS^CFA,30^FO120,190^FDName    :Sachin Pangare^FS^FO120,230^FDCompany :Appstek ^FS^FO120,270^FDVehicle :AP09BU8621^FS^FO120,310^FDDate    :20-04-2022^FS^FO120,350^FDTime    :1:01 PM^FS^CFA,15^XZ"); //---- sample 2

                } catch (Exception e) {
                    setStatus("Exception" + e.getMessage(), Color.RED);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void printWithBluetooth() {

        if (isBluetoothSelected()) {
            setBluetooth();
        }
    }

    private void usbInit() {
        strType = "USB";
        mUsbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
        if (mUsbManager != null) {
            mDeviceList = mUsbManager.getDeviceList();
        }
        if (mDeviceList.size() > 0) {
            mDeviceIterator = mDeviceList.values().iterator();
            Toast.makeText(getApplicationContext(), "Device List Size: " + mDeviceList.size(), Toast.LENGTH_SHORT).show();
            while (mDeviceIterator.hasNext()) {
                UsbDevice usbDevice1 = mDeviceIterator.next();
              /*  usbDevice1 += "\n" + "DeviceID: " + usbDevice1.getDeviceId() + "\n" +
                        "DeviceName: " + usbDevice1.getDeviceName() + "\n" +
                        "Protocol: " + usbDevice1.getDeviceProtocol() + "\n" +
                        "Product Name: " + usbDevice1.getProductName() + "\n" +
                        "Manufacturer Name: " + usbDevice1.getManufacturerName() + "\n" +
                        "DeviceSubClass: " + usbDevice1.getDeviceSubclass() + "\n" +
                        "VendorID: " + usbDevice1.getVendorId() + "\n" +
                        "ProductID: " + usbDevice1.getProductId() + "\n";*/
                Log.d("UsbDeviceDetails","Device VendorID & ProductID : " + usbDevice1.getVendorId() + "\n" + usbDevice1.getProductId());

                int interfaceCount = usbDevice1.getInterfaceCount();
                mDevice = usbDevice1;
                Toast.makeText(getApplicationContext(), "Device VendorID & ProductID : " + usbDevice1.getVendorId() + "\n" + usbDevice1.getProductId() + "InterfaceCount" + interfaceCount, Toast.LENGTH_SHORT).show();
            }
            mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            getApplicationContext().registerReceiver(mUsbReceiver, filter);
            mUsbManager.requestPermission(mDevice, mPermissionIntent);
        } else {
            Toast.makeText(getApplicationContext(), "Please attach printer via USB", Toast.LENGTH_SHORT).show();
        }
    }


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            mInterface = device.getInterface(0);
                            mEndPoint = mInterface.getEndpoint(1);// 0 IN and  1 OUT to printer.
                            mConnection = mUsbManager.openDevice(device);
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "PERMISSION DENIED FOR THIS DEVICE", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    Toast.makeText(getApplicationContext(), "USB Device ATTACHED", Toast.LENGTH_SHORT).show();
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    Toast.makeText(getApplicationContext(), "USB Device detached", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };


    @SuppressLint("MissingPermission")
    public void setBluetooth() {

        if (SettingsHelper.getBluetoothAddress(this).isEmpty()) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!bluetoothAdapter.isEnabled()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, ENABLE_BLUETOOTH);

            } else {
                showBluetoothList();
            }
        } else {
            sendTSPLOverBluetooth(SettingsHelper.getBluetoothAddress(this));
        }
    }

    private void sendTSPLOverBluetooth(String bluetoothAddress) {
        new Thread(() -> {
            try {
                TscDll.openport(bluetoothAddress);
                //Set label width, height, printing speed, printing density, sensor type, gap/black mark vertical spacing, gap/black mark offset distance)
                // printing density is increasing the font & density of the text i.e density = 15 the tis looking little bit large & if density = 4 then its looking good.
                TscDll.setup(80, 51, 4, 4, 0, 3, 0);
                TscDll.clearbuffer();
                TscDll.printerfont(40, 40, "4", 0, 1, 1, "SKC " + "XYZ" + "-" + "Visitor Pass");
                TscDll.printerfont(30, 70, "3", 0, 1, 1, "__________________________________");
                TscDll.printerfont(44, 110, "3", 0, 1, 1, "Visitor ID : " + "May2200001");
                TscDll.printerfont(44, 150, "3", 0, 1, 1, "Name       : " + "ABC");
                TscDll.printerfont(44, 190, "3", 0, 1, 1, "Company    : " + "XYZ");
                TscDll.printerfont(44, 230, "3", 0, 1, 1, "Vehicle    : " + "AB45DS0987");
                TscDll.printerfont(44, 270, "3", 0, 1, 1, "Date & Time: " + "12-05-2022" + " " + "12:26 PM");
                TscDll.printerfont(44, 310, "3", 0, 1, 1, "Approved By: " + "9561******");
                TscDll.printlabel(1, 1);
                TscDll.closeport(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @SuppressLint({"InflateParams", "MissingPermission"})
    private void showBluetoothList() {
        if (!bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.startDiscovery();
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        dialogView = inflater.inflate(R.layout.printer_list, null);
        adapter1 = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList_bonded);
        lv1 = dialogView.findViewById(R.id.listView1);
        btn_scan = dialogView.findViewById(R.id.btn_scan);
        LLlayout = dialogView.findViewById(R.id.ll1);
        lv2 = dialogView.findViewById(R.id.listView2);
        adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList_found);
        lv1.setAdapter(adapter1);
        lv2.setAdapter(adapter2);
        dialog = new AlertDialog.Builder(this).setTitle("Bluetooth List").setView(dialogView).create();
        dialog.show();

        myDevice = new DeviceReceiver(deviceList_found, adapter2, lv2);
        //register the receiver
        IntentFilter filterStart = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter filterEnd = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(myDevice, filterStart);
        registerReceiver(myDevice, filterEnd);

        setDlistener();
        findAvalibleDevice();
    }

    @SuppressLint("MissingPermission")
    private void setDlistener() {

        btn_scan.setOnClickListener(v -> {
            LLlayout.setVisibility(View.VISIBLE);
            //btn_scan.setVisibility(View.GONE);
        });

        //boned device connect
        lv1.setOnItemClickListener((arg0, arg1, arg2, arg3) -> {

            try {

                if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                String msg = deviceList_bonded.get(arg2);
                mac = msg.substring(msg.length() - 17);
                String name = msg.substring(0, msg.length() - 18);
                //lv1.setSelection(arg2);
                dialog.cancel();
                macAddressEditText.setText(mac);
                SettingsHelper.saveBluetoothAddress(MainActivity.this, mac);
                //Log.i("TAG", "mac="+mac);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });

        //found device and connect device
        lv2.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @SuppressLint("MissingPermission")
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                // TODO Auto-generated method stub

                    if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    String msg = deviceList_found.get(arg2);
                    mac = msg.substring(msg.length() - 17);
                    String name = msg.substring(0, msg.length() - 18);
                    //lv2.setSelection(arg2);
                    dialog.cancel();
                    macAddressEditText.setText(mac);
                    Log.i("TAG", "mac=" + mac);

            }
        });
    }

    @SuppressLint("MissingPermission")
    private void findAvalibleDevice() {

        Set<BluetoothDevice> device = bluetoothAdapter.getBondedDevices();
        deviceList_bonded.clear();
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            adapter1.notifyDataSetChanged();
        }
        if (device.size() > 0) {
            //already
            for (BluetoothDevice btd : device) {
                deviceList_bonded.add(btd.getName() + '\n' + btd.getAddress());
                adapter1.notifyDataSetChanged();
            }
        } else {
            deviceList_bonded.add("No can be matched to use bluetooth");
            adapter1.notifyDataSetChanged();
        }
    }

    public void onActivityResult(int mRequestCode, int mResultCode, Intent mDataIntent) {
        super.onActivityResult(mRequestCode, mResultCode, mDataIntent);
        if (mRequestCode == ENABLE_BLUETOOTH) {
            if (mResultCode == Activity.RESULT_OK) {
                showBluetoothList();
            }
        }
    }

    private void toggleEditField(EditText editText, boolean set) {
        editText.setEnabled(set);
        editText.setFocusable(set);
        editText.setFocusableInTouchMode(set);
    }

    private boolean isBluetoothSelected() {
        return btRadioButton.isChecked();
    }

    private String getMacAddressFieldText() {
        return macAddressEditText.getText().toString();
    }


    @RequiresApi(api = Build.VERSION_CODES.S)
    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), BLUETOOTH_CONNECT);
        int result2 = ContextCompat.checkSelfPermission(getApplicationContext(), BLUETOOTH_SCAN);

        return result == PackageManager.PERMISSION_GRANTED && result2 == PackageManager.PERMISSION_GRANTED ;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestPermission() {

        ActivityCompat.requestPermissions(this, new String[]{BLUETOOTH_CONNECT,BLUETOOTH_SCAN}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE)
        {
            if (grantResults.length > 0) {

                boolean locationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean cameraAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (locationAccepted && cameraAccepted)
                    Toast.makeText(getApplicationContext(), "Permission Granted", Toast.LENGTH_SHORT).show();
                else {
                    Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    {

                        if (shouldShowRequestPermissionRationale(BLUETOOTH_CONNECT))
                        {
                            showMessageOKCancel("You need to allow access to both the permissions",
                                    (dialog, which) -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            requestPermissions(new String[]{BLUETOOTH_CONNECT,
                                                           BLUETOOTH_SCAN},
                                                    PERMISSION_REQUEST_CODE);
                                        }
                                    });
                        }
                    }

                }
            }
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

}

