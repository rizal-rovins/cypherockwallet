package jp.sugnakys.usbserialconsole;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.sugnakys.usbserialconsole.api.ClientBuilder;
import jp.sugnakys.usbserialconsole.model.AddressResponse;
import jp.sugnakys.usbserialconsole.model.PushTx;
import jp.sugnakys.usbserialconsole.settings.SettingsActivity;
import jp.sugnakys.usbserialconsole.util.Constants;
import jp.sugnakys.usbserialconsole.util.Log;
import jp.sugnakys.usbserialconsole.util.Util;
import jp.sugnakys.usbserialconsole.utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseAppCompatActivity
        implements View.OnClickListener, TextWatcher {

    private static final String TAG = "MainActivity";
    private static final String RECEIVED_TEXT_VIEW_STR = "RECEIVED_TEXT_VIEW_STR";

    private UsbService usbService;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    private LinearLayout mainLayout;
    private Button sendBtn;
    private EditText sendMsgView;
    private EditText amount;
    private LinearLayout sendViewLayout;
    private TextView receivedMsgView;
    private ScrollView scrollView;
    private Button pushtx;
    private StringBuilder data = new StringBuilder(180);
    private long finalBalance;

    private Menu mOptionMenu;

    private MyHandler mHandler;

    private String timestampFormat;
    private String lineFeedCode;
    private String tmpReceivedData = "";
    private long scriptLength = 19; //In Hex

    private boolean showTimeStamp;
    private boolean isUSBReady = false;
    private boolean isConnect = false;
    private StringBuilder receivedData = new StringBuilder(128);
    private StringBuilder tempReceivedData = new StringBuilder(128);

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED:
                    Toast.makeText(context,
                            getString(R.string.usb_permission_granted),
                            Toast.LENGTH_SHORT).show();
                    isUSBReady = true;
                    updateOptionsMenu();
                    requestConnection();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED:
                    Toast.makeText(context,
                            getString(R.string.usb_permission_not_granted),
                            Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB:
                    Toast.makeText(context,
                            getString(R.string.no_usb),
                            Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED:
                    Toast.makeText(context,
                            getString(R.string.usb_disconnected),
                            Toast.LENGTH_SHORT).show();
                    isUSBReady = false;
                    stopConnection();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED:
                    Toast.makeText(context, getString(R.string.usb_not_supported),
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Log.e(TAG, "Unknown action");
                    break;
            }
        }
    };

    private void requestConnection() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setMessage(getString(R.string.confirm_connect));
        alertDialog.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startConnection();
            }
        });
        alertDialog.setNegativeButton(getString(android.R.string.cancel), null);
        alertDialog.create().show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme_NoActionBar);

        getTransactionDetails();

        mHandler = new MyHandler(this);

        setContentView(R.layout.activity_main);

        mainLayout = (LinearLayout) findViewById(R.id.mainLayout);
        receivedMsgView = (TextView) findViewById(R.id.receivedMsgView);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        pushtx = (Button) findViewById(R.id.buttonPushTransaction);
        sendBtn = (Button) findViewById(R.id.sendBtn);
        sendMsgView = (EditText) findViewById(R.id.sendMsgView);
        sendViewLayout = (LinearLayout) findViewById(R.id.sendViewLayout);
        amount = (EditText) findViewById(R.id.amount);

        sendBtn.setOnClickListener(this);
        pushtx.setOnClickListener(this);
        sendMsgView.addTextChangedListener(this);
        sendBtn.setEnabled(true);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setDefaultColor() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();

        if (!pref.contains(getString(R.string.color_console_background_key))) {
            int defaultBackgroundColor = Color.TRANSPARENT;
            Drawable background = mainLayout.getBackground();
            if (background instanceof ColorDrawable) {
                defaultBackgroundColor = ((ColorDrawable) background).getColor();
            }
            editor.putInt(getString(R.string.color_console_background_key), defaultBackgroundColor);
            editor.apply();
            Log.d(TAG, "Default background color: " + String.format("#%08X", defaultBackgroundColor));
        }

        if (!pref.contains(getString(R.string.color_console_text_key))) {
            int defaultTextColor = receivedMsgView.getTextColors().getDefaultColor();
            editor.putInt(getString(R.string.color_console_text_key), defaultTextColor);
            editor.apply();
            Log.d(TAG, "Default text color: " + String.format("#%08X", defaultTextColor));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(RECEIVED_TEXT_VIEW_STR, receivedMsgView.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        receivedMsgView.setText(savedInstanceState.getString(RECEIVED_TEXT_VIEW_STR));
    }

    @Override
    public void onResume() {
        super.onResume();

        setDefaultColor();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        showTimeStamp = pref.getBoolean(
                getResources().getString(R.string.timestamp_visible_key), true);
        timestampFormat = pref.getString(getString(R.string.timestamp_format_key),
                getString(R.string.timestamp_format_default));
        lineFeedCode = Util.getLineFeedCd(
                pref.getString(getString(R.string.line_feed_code_send_key),
                        getString(R.string.line_feed_code_cr_lf_value)),
                this);
        if (pref.getBoolean(getString(R.string.send_form_visible_key), true)) {
            sendViewLayout.setVisibility(View.VISIBLE);
        } else {
            sendViewLayout.setVisibility(View.GONE);
        }

        if (pref.getBoolean(getString(R.string.sleep_mode_key), false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        int backgroundColor = pref.getInt(getString(R.string.color_console_background_key), Color.WHITE);
        Log.d(TAG, "Background color: " + String.format("#%08X", backgroundColor));
        mainLayout.setBackgroundColor(backgroundColor);

        int textColor = pref.getInt(getString(R.string.color_console_text_key), Color.BLACK);
        Log.d(TAG, "Text color: " + String.format("#%08X", textColor));
        receivedMsgView.setTextColor(textColor);
        sendMsgView.setTextColor(textColor);

        setFilters();
        startService(UsbService.class, usbConnection);
        updateOptionsMenu();
    }

    @Override
    public void onDestroy() {
        if(isConnect) {
            stopConnection();
        }
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);

        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
            alertDialog.setMessage(getString(R.string.confirm_finish_text));
            alertDialog.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            });
            alertDialog.setNegativeButton(getString(android.R.string.cancel), null);
            alertDialog.create().show();
            return true;
        }
        return false;
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionMenu = menu;
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void updateOptionsMenu() {
        if (mOptionMenu != null) {
            onPrepareOptionsMenu(mOptionMenu);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_connect);
        item.setEnabled(isUSBReady);
        if (isConnect) {
            item.setTitle(getString(R.string.action_disconnect));
        } else {
            item.setTitle(getString(R.string.action_connect));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_connect:
                android.util.Log.d(TAG, "Connect clicked");
                if (isConnect) {
                    stopConnection();
                } else {
                    startConnection();
                }
                break;
            case R.id.action_clear_log:
                Log.d(TAG, "Clear log clicked");
                receivedMsgView.setText("");
                break;
            case R.id.action_save_log:
                Log.d(TAG, "Save log clicked");
                writeToFile(receivedMsgView.getText().toString());
                break;
            case R.id.action_settings:
                Log.d(TAG, "Settings clicked");
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_log_list:
                Log.d(TAG, "Log list clicked");
                intent = new Intent(this, LogListViewActivity.class);
                startActivity(intent);
                break;
            default:
                Log.e(TAG, "Unknown id");
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void writeToFile(String data) {
        String fileName = Util.getCurrentDateForFile() + Constants.LOG_EXT;
        File dirName = Util.getLogDir(getApplicationContext());

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(dirName, fileName));
            fos.write(data.getBytes(Constants.CHARSET));
            Log.d(TAG, "Save: " + fileName);
            Toast.makeText(this, getString(R.string.action_save_log)
                    + " : " + fileName, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void sendMessage(String msg) {
        Pattern pattern = Pattern.compile("\n$");
        Matcher matcher = pattern.matcher(msg);
        String strResult = matcher.replaceAll("") + lineFeedCode;
        try {
            usbService.write(strResult.getBytes(Constants.CHARSET));
            Log.d(TAG, "SendMessage: " + msg);
            addReceivedData(msg);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void startConnection() {
        usbService.setHandler(mHandler);
        isConnect = true;
        Toast.makeText(getApplicationContext(),
                getString(R.string.start_connection),Toast.LENGTH_SHORT).show();
        updateOptionsMenu();
    }

    private void stopConnection() {
        usbService.setHandler(null);
        isConnect = false;
        Toast.makeText(getApplicationContext(),
                getString(R.string.stop_connection),Toast.LENGTH_SHORT).show();
        updateOptionsMenu();
    }

    private void storeReceivedData(String data) {
        tempReceivedData.append(data);
        if(tempReceivedData.length() >= 128) {
            receivedData.insert(0, tempReceivedData);
            Log.d("received data length", "Complete signature received");
            Toast.makeText(this, "signature received", Toast.LENGTH_SHORT).show();
            pushtx.setEnabled(true);
            tempReceivedData.delete(0, tempReceivedData.length());
        }
    }

    private void addReceivedData(String data) {
        if (showTimeStamp) {
            addReceivedDataWithTime(data);
        } else {
            addTextView(data);
        }
    }

    private void addTextView(String data) {
        receivedMsgView.append(data);
        scrollView.scrollTo(0, receivedMsgView.getBottom());
    }

    private void addReceivedDataWithTime(String data) {
        String timeStamp = "[" + Util.getCurrentTime(timestampFormat) + "] ";

        tmpReceivedData += data;
        String separateStr = getLineSeparater(tmpReceivedData);
        if (!separateStr.isEmpty()) {
            String[] strArray = tmpReceivedData.split(separateStr);
            tmpReceivedData = "";
            for (int i = 0; i < strArray.length; i++) {
                if (strArray.length != 1
                        && i == (strArray.length - 1)
                        && !strArray[i].isEmpty()) {
                    tmpReceivedData = strArray[i];
                } else {
                    addTextView(timeStamp + strArray[i] + System.lineSeparator());
                }
            }
        }
    }

    private String getLineSeparater(String str) {
        if (str.contains(Constants.CR_LF)) {
            return Constants.CR_LF;
        } else if (str.contains(Constants.LF)) {
            return Constants.LF;
        } else if (str.contains(Constants.CR)) {
            return Constants.CR;
        } else {
            return "";
        }
    }

    private void appendData() {

        //TODO : take the input of value from the user here

        //As an example, taking 40,000 satoshis everytime
        StringBuilder s = new StringBuilder(8);
        int amountNum = Integer.parseInt(amount.getText().toString());
        byte[] valueHex = ByteBuffer.allocate(4).putInt(amountNum).array();
        int[] valueHexInInt = Utils.bytearray2intarray(valueHex);
        valueHexInInt = Utils.reverseIntArray(valueHexInInt);

        for(int i = 0;i < valueHexInInt.length;i++) {
            s.append(String.format("%02x", valueHexInInt[i]));
        }

        for(int i = 0;i < 8;i++) {
            s.append(0);
        }

//                    byte[] valueHexBytes = Utils.hexStringToByteArray(valueHex);
//                    int[] valueHexBytesInInt = Utils.bytearray2intarray(valueHexBytes);
////                    valueHexBytesInInt = Utils.reverseIntArray(valueHexBytesInInt);
//
//                    int[] finalValueHexBytesInInt = new int[8];
//                    int len = valueHexBytesInInt.length;
//                    for(int i = 0; i < 8; i++) {
//                        if(i < len) {
//                            finalValueHexBytesInInt[i] = valueHexBytesInInt[i];
//                        } else {
//                            finalValueHexBytesInInt[i] = 0;
//                        }
//                    }

        data.append(s.toString());
//                    for(int i = 0; i < 8; i++) {
//                        data[57 + i] = (byte) finalValueHexBytesInInt[i];
//                    }

        data.append(scriptLength);

        //Sending the money to a public address
        //TODO : take the input of the receiver address from the user here
//                    String receiverAddress = "mqLzxcT45f51Pk9BirVNfDBEZEdzNJsZvn";
        String receiverAddress = sendMsgView.getText().toString();
        byte[] receiverAddressBytes = Utils.getHash160FromAddress(receiverAddress);
        int[] receiverAddressBytesInInt = Utils.bytearray2intarray(receiverAddressBytes);
        int[] finalReceiverAddressBytesInInt = new int[25];
        finalReceiverAddressBytesInInt[0] = 118;
        finalReceiverAddressBytesInInt[1] = 169;
        finalReceiverAddressBytesInInt[2] = 20;
        finalReceiverAddressBytesInInt[23] = 136;
        finalReceiverAddressBytesInInt[24] = 172;

        for(int i = 0; i < 20; i++) {
            finalReceiverAddressBytesInInt[i + 3] = receiverAddressBytesInInt[i];
        }

        StringBuilder temp1 = new StringBuilder(50);

        for(int i = 0;i < 25;i++) {
            //String temp = Integer.toHexString(finalReceiverAddressBytesInInt[i]);
            String temp = String.format("%02x", finalReceiverAddressBytesInInt[i]);
            temp1.append(temp);
        }

        data.append(temp1.toString());

//                    for(int i = 0;i < 25;i++) {
//                       data[65 + i] = (byte)  finalReceiverAddressBytesInInt[i];
//                    }

//                    data.append(receiverAddress);


        //Change Value returned back to the sender

        StringBuilder changeS = new StringBuilder(8);

        byte[] changeValueHex = ByteBuffer.allocate(4).putInt((int)(finalBalance - amountNum - 10000)).array();
        int[] changeValueHexInInt = Utils.bytearray2intarray(changeValueHex);
//                    changeValueHexInInt = Utils.reverseIntArray(changeValueHexInInt);

        for(int i = changeValueHexInInt.length - 1; i >= 0; i--) {
            changeS.append(String.format("%02x", changeValueHexInInt[i]));
        }

        data.append(changeS.toString());

        for(int i=0;i<16 - changeS.length();i++) {
            data.append(0);
        }

//                    for(int i = 0; i < 8; i++) {
//                        data[57 + i] = (byte) finalValueHexBytesInInt[i];
//                    }

        data.append(scriptLength);

        //Sending the money to a public address
        //TODO : take the input of the receiver address from the user here
        String changeAddress = "msMSr52jyP8HV3Kx9uWRPFHHU1KPPJpYX9";
        byte[] changeAddressBytes = Utils.getHash160FromAddress(changeAddress);
        int[] changeAddressBytesInInt = Utils.bytearray2intarray(changeAddressBytes);
        int[] finalChangeAddressBytesInInt = new int[25];
        finalChangeAddressBytesInInt[0] = 118;
        finalChangeAddressBytesInInt[1] = 169;
        finalChangeAddressBytesInInt[2] = 20;
        finalChangeAddressBytesInInt[23] = 136;
        finalChangeAddressBytesInInt[24] = 172;

        for(int i = 0; i < 20; i++) {
            finalChangeAddressBytesInInt[i + 3] = changeAddressBytesInInt[i];
        }

        StringBuilder changeString = new StringBuilder(50);

        for(int i = 0;i < 25;i++) {
//                        String temp = Integer.toHexString(finalChangeAddressBytesInInt[i]);
            String temp = String.format("%02x", finalChangeAddressBytesInInt[i]);
            changeString.append(temp);
        }

        data.append(changeString.toString());

        byte[] lockTimeInBytes = ByteBuffer.allocate(4).putInt(0).array();
        for(int i = 0;i < 4;i++) {
            data.append(0);
            data.append(lockTimeInBytes[i]);
        }

        byte[] sigHashCode = ByteBuffer.allocate(4).putInt(1).array();
        for(int i = 3;i >= 0;i--) {
            data.append(0);
            data.append(sigHashCode[i]);
        }

//                    //Delete sighash
//                    data.delete(288,296);
//                    //Replace script length
//                    data.replace(82, 84, "6a");

        int[] lockTime = {0, 0, 0, 0};

        //TODO : replace the constant receiver address with user input
        //TODO : send 128 bytes at single time.
        //TODO : after the callback from the device, send the
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.sendBtn:
                android.util.Log.d(TAG, "Send button clicked");
                appendData();
                String message = data.toString();
                if (!message.isEmpty()) {
                    message += System.lineSeparator();
                    sendMessage(message);
                    sendMsgView.setText("");
                }
                break;
            case R.id.buttonPushTransaction:
                android.util.Log.d(TAG, "PushTx button clicked");

                StringBuilder realTxString = new StringBuilder();
                StringBuilder scriptSig = new StringBuilder();

                int opcode = 47;
                int header = 30;
                int sigLength = 44;
                String integerC = "02";
                int RSLength = 20;
                int pushDataOpCode = 21;
                String sigCode = "01";
                int[] compressedSenderPublicAddress = new int[]{2,129,68,240,97,124,169,244,111,175,34,195,162,170,190,43,90,112,180,27,51,217,67,238,222,171,69,251,14,163,157,176,59};

                scriptSig.append(opcode);
                scriptSig.append(header);
                scriptSig.append(sigLength);
                scriptSig.append(integerC);
                scriptSig.append(RSLength);
                receivedData.insert(64, "0220");
                scriptSig.append(receivedData);
                scriptSig.append(sigCode);
                scriptSig.append(pushDataOpCode);

                realTxString.append(data);

                //Delete sighash
                realTxString.delete(288,296);
                //Replace script length
                realTxString.replace(82, 84, "6a");

                int temp1 = Integer.parseInt(receivedData.substring(0, 2), 16);
                int temp2 = Integer.parseInt(receivedData.substring(68, 70), 16);

                if((Integer.parseInt(receivedData.substring(0, 2), 16) > 127) && (Integer.parseInt(receivedData.substring(68, 70), 16) > 127)) {

                    realTxString.replace(82, 84, "6c");
                    scriptSig.replace(0, 2, "49");
                    scriptSig.replace(4, 6, "46");
                    scriptSig.replace(8, 10, "2100");
                    scriptSig.replace(78, 80, "2100");

                } else if(Integer.parseInt(receivedData.substring(0, 2), 16) > 127) {

                    realTxString.replace(82, 84, "6b");
                    scriptSig.replace(0, 2, "48");
                    scriptSig.replace(4, 6, "45");
                    scriptSig.replace(8, 10, "2100");

                } else if(Integer.parseInt(receivedData.substring(68, 70), 16) > 127) {

                    realTxString.replace(82, 84, "6b");
                    scriptSig.replace(0, 2, "48");
                    scriptSig.replace(4, 6, "45");
                    scriptSig.replace(76, 78, "2100");
                }

                receivedData.delete(0, receivedData.length());

                for(int i = 0; i < compressedSenderPublicAddress.length; i++) {
                    scriptSig.append(String.format("%02x", compressedSenderPublicAddress[i]));
                }

                //Replace scriptpubkey with Received data
                realTxString.replace(84,134, scriptSig.toString());

                PushTx pushTx = new PushTx(realTxString.toString());

                new ClientBuilder(Constants.BASE_URL_PUSHTX).getBlockchainApi().pushTx(pushTx, "2f0a91ede7634dcfa99291e97146ddd8").enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        try {
                            String callObject = call.request().toString();
                            String callbody = call.request().body().toString();
                            String responseTxObject  = response.body();
//                            Log.d("response from bc", responseTxObject);
                            addReceivedData(data.toString());
                            pushtx.setEnabled(false);
                            getTransactionDetails();
                            if(response.code() == 201) {
                                Toast.makeText(getApplicationContext(),
                                        "Pushtx successful", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        t.printStackTrace();
                        Toast.makeText(getApplicationContext(), "Push successful", Toast.LENGTH_LONG).show();
                    }
                });
                break;
            default:
                android.util.Log.e(TAG, "Unknown view");
                break;
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    if (data != null) {
                        mActivity.get().storeReceivedData(data);
                        mActivity.get().addReceivedData(data);
                    }
                    break;
                case UsbService.CTS_CHANGE:
                    Log.d(TAG, "CTS_CHANGE");
                    Toast.makeText(mActivity.get(), "CTS_CHANGE", Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Log.d(TAG, "DSR_CHANGE");
                    Toast.makeText(mActivity.get(), "DSR_CHANGE", Toast.LENGTH_LONG).show();
                    break;
                default:
                    Log.e(TAG, "Unknown message");
                    break;
            }
        }
    }

    private void getTransactionDetails() {

        new ClientBuilder(Constants.BASE_URL).getBlockchainApi().addressDetails("msMSr52jyP8HV3Kx9uWRPFHHU1KPPJpYX9").enqueue(new Callback<AddressResponse>() {
            @Override
            public void onResponse(@NonNull Call<AddressResponse> call, @NonNull Response<AddressResponse> response) {

                try {
//                    data = new byte[90];
                    data.delete(0, data.length());
                    long version = response.body().getTxs().get(0).getVer();

                    int[] versionBytes = {0, 0, 0, 0};
                    versionBytes[0] = (int) version;

                    for(int i = 0;i<4;i++) {
                        data.append(0);
                        data.append(versionBytes[i]);
                    }

                    android.util.Log.d("byte result", String.valueOf(versionBytes));

                    int inSize = (int)response.body().getTxs().get(0).getVinSz();
                    data.append(0);
                    data.append(Integer.toHexString(inSize));

                    String txHash = response.body().getTxs().get(0).getHash();
                    byte[] txHashBytes = Utils.hexStringToByteArray(txHash);
                    int[] txHashBytesInInt = Utils.bytearray2intarray(txHashBytes);
//                    txHashBytesInInt = Utils.reverseIntArray(txHashBytesInInt);

                    for(int i = 31; i >= 0; i--) {
                        data.append(String.format("%02x", txHashBytesInInt[i]));
                    }

                    long n = response.body().getTxs().get(0).getOut().get(1).getN();
//                    int[] ns = {0, 0, 0, 0};
//                    ns[0] = (int) n;

                    byte[] prevOutIndex = ByteBuffer.allocate(4).putInt((int)n).array();
                    for(int i = 3;i >= 0;i--) {
                        data.append(0);
                        data.append(prevOutIndex[i]);
                    }

                    data.append(scriptLength);

                    String scriptPubKeyPrev = response.body().getTxs().get(0).getOut().get(1).getScript();
                    byte[] scriptPubKeyPrevBytes = Utils.hexStringToByteArray(scriptPubKeyPrev);
                    int[] scriptPubKeyPrevBytesInInt = Utils.bytearray2intarray(scriptPubKeyPrevBytes);

//                    for(int i = 0; i < 25 ;i++) {
//                        data[i + 32] = (byte) scriptPubKeyPrevBytesInInt[i];
//                    }

                    data.append(scriptPubKeyPrev);

                    int[] sequence = {255, 255, 255, 255};

                    data.append("ffffffff"); // Constant value of sequence

                    int noOfOutputs = (int) response.body().getTxs().get(0).getVoutSz();
                    data.append(0);
                    data.append(Integer.toHexString(noOfOutputs));

                    finalBalance = response.body().getFinalBalance();

                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(@NonNull Call<AddressResponse> call, @NonNull Throwable t) {
                Log.d("error url", call.request().url().toString());
                t.printStackTrace();
                Toast.makeText(getApplicationContext(), "failed to make the call", Toast.LENGTH_LONG).show();
            }
        });
    }
}
