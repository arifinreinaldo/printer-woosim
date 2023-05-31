package net.simplr.woosimdp230l;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dascom.print.connection.BluetoothConnection;
import com.dascom.print.utils.BluetoothUtils;
import com.woosim.printer.WoosimCmd;
import com.woosim.printer.WoosimImage;
import com.woosim.printer.WoosimService;

import net.simplr.woosimdp230l.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity implements MainPresenter.View {
    SharedPreferences sp;
    int index = 0;
    private final String sp_file = "woosimdp230lmac";
    private final String sp_mac = "macaddress";
    private String mac;
    private MainPresenter presenter;
    private String[] arrArgs;
    ActivityMainBinding binding;
    BluetoothConnection bluetoothConnection;
    AdapterDevice adapter;
    List<BluetoothDevice> listDevice = new ArrayList<>();
    String action, value, printerType;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Log.d("Printer", "onNewIntent: ");
        processData();
    }

    private void processData() {
        Intent intent = getIntent();
        printerType = intent.getStringExtra("PrinterType");
        if (printerType == null) {
            printerType = "";
        }
        arrArgs = intent.getStringArrayExtra("ARR_TO_PRINT");
        if (arrArgs == null) {
            arrArgs = new String[10];
//            arrArgs[0] = "PDF:com.simplrsales/Photo/Html3.pdf";
            arrArgs[0] = "Text:Tes Printing data Again";
            arrArgs[1] = "Text:Tes Printing data Again";
            arrArgs[2] = "Text:3";
            arrArgs[3] = "Text:4";
            arrArgs[4] = "Text:5";
            arrArgs[5] = "Text:6";
            arrArgs[6] = "Text:7";
            arrArgs[7] = "Text:8";
            arrArgs[8] = "Text:9";
            arrArgs[9] = "Text:10";
//            arrArgs[2] = "Image:com.simplrsales/Photo/GHL01I000051.png";
        }
        String savedMac = sp.getString(sp_mac, "");
        if (!savedMac.isEmpty()) {
            connectDevice(savedMac);
        }

        if (printerType.equalsIgnoreCase("GHLWoosim")) {
            Log.d("length", arrArgs.length + " rows");
            if (arrArgs.length > 0) {
                index = 0;
                try {
                    while (mPrintService.getState() != BluetoothPrintService.STATE_CONNECTED) {
                        Thread.sleep(100);
                    }
                    doPrintWoosim(arrArgs);
                } catch (Exception e) {

                }
            } else {
                closeActivity(true, "No Data to Print from Simplr Application");
            }
        } else {
            closeActivity(true, "Call from Simplr Application");
        }
    }

    private void doPrintWoosim(String[] arrArgs) {
        if (index == arrArgs.length) {
            try {
                Thread.sleep(1000);
                if (mPrintService != null) {
                    mPrintService.stop();
                    mPrintService = null;
                }
                closeActivity(true, "Done Printing");
            } catch (Exception e) {

            }
        } else {
            String sentence = arrArgs[index];
            ExecutorService threadpool = Executors.newCachedThreadPool();
            if (sentence.startsWith("Image:")) {
                sentence = sentence.replaceFirst("Image:", "");
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    printImage(sentence);
                } else {
                    Toast.makeText(this, "File Permission is not approved", Toast.LENGTH_SHORT).show();
                }
            } else if (sentence.startsWith("Text:")) {
                sentence = sentence.replaceFirst("Text:", "");
                printText(sentence);
            } else if (sentence.startsWith("PDF:")) {
                sentence = sentence.replaceFirst("PDF:", "");
                String finalSentence = sentence;
                try {
//                    Future<String> futureTask = threadpool.submit(() -> printPDF(finalSentence));
//                    while (!futureTask.isDone()) {
//                        Thread.sleep(100);
//                        Log.d("Printing", "NOT DOne");
//                    }
                    printPDF(finalSentence);
                    threadpool.shutdown();
                } catch (Exception e) {

                }
            }
            index++;
            doPrintWoosim(arrArgs);
        }
    }

    private String printPDF(String path) {
        String rtn = "";
        try {
            if (!path.isEmpty()) {
                File extPath = Environment.getExternalStoragePublicDirectory(path);
                if (extPath.exists()) {
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(extPath,
                            ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    int paperWidth = 576;
                    for (int i = 0; i < renderer.getPageCount(); i++) {
                        PdfRenderer.Page page = renderer.openPage(i);
// The destination bitmap format must be ARGB.
// Original page is resized to fit roll paper width.
                        Bitmap bmp = Bitmap.createBitmap(
                                paperWidth,
                                page.getHeight() * paperWidth / page.getWidth(),
                                Bitmap.Config.ARGB_8888);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        sendData(WoosimImage.printCompressedBitmap(
                                0, 0, bmp.getWidth(), bmp.getHeight(), bmp));
                        bmp.recycle();
                        page.close();
                        Thread.sleep(2000);
                    }
                    sendData(WoosimCmd.PM_setStdMode());
                    renderer.close();
                }
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        } finally {
//            threadpool.shutdown();
        }
        return rtn;
    }

    private void sendData(byte[] data) {
        if (mPrintService.getState() != BluetoothPrintService.STATE_CONNECTED) {
            Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("Printing", "Prepare Printing");
        if (data.length > 0) {
            try {
                mPrintService.write(data);
            } catch (Exception e) {

            }
        }
    }

    Bitmap decodeBase64(String base64Image) {
        byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    String encodeBase64(File file) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] fileBytes = new byte[(int) file.length()];
            fileInputStream.read(fileBytes);
            fileInputStream.close();
            return Base64.encodeToString(fileBytes, Base64.DEFAULT);
        } catch (Exception e) {

        }
        return "";
    }

    public void printImage(String path) {
        String image = "/9j/4AAQSkZJRgABAQEAYABgAAD/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAEAAAAAAAD/2wBDAAIBAQIBAQICAgICAgICAwUDAwMDAwYEBAMFBwYHBwcGBwcICQsJCAgKCAcHCg0KCgsMDAwMBwkODw0MDgsMDAz/2wBDAQICAgMDAwYDAwYMCAcIDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAz/wAARCAEfAhsDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD9/KKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAoooo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pI08tcU6igAooooAKKKKACiiigAooooAKKKKACiiigAb7tV4LUbyzZLdiWJxViigAAxRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABQRmiigBuynFQe1FFAAF20UUUrAFFFFMAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigD/2Q==";
        ExecutorService threadpool = Executors.newCachedThreadPool();
        try {
            if (!path.isEmpty()) {
                File extPath = Environment.getExternalStoragePublicDirectory(path);
                if (extPath.exists()) {
                    image = encodeBase64(extPath);
                }

                Bitmap bmp = decodeBase64(image);
                if (bmp == null) {
                    Log.e(TAG, "resource decoding is failed");
                    return;
                }
                int width = bmp.getWidth();
                //192 is 1 inch
                //384 is 2 inch
                //576 is 3 inch
                //832 is 4 inch
                if (width > 576) {
                    int height = bmp.getHeight();
                    height = (int) (576 * height / width);
                    width = 576;
                    bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
                }
                Bitmap finalBmp = bmp;
                Future<byte[]> futureTask = threadpool.submit(() -> WoosimImage.printBitmap(0, 0, finalBmp.getWidth(), finalBmp.getHeight(), finalBmp));
                while (!futureTask.isDone()) {
                    Thread.sleep(100);
                }
                byte[] data = futureTask.get();
                Log.d("BMP ", data.length + " data");
                bmp.recycle();

                sendData(WoosimCmd.initPrinter());
                sendData(WoosimCmd.setPageMode());
                sendData(data);
                sendData(WoosimCmd.PM_setStdMode());
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        } finally {
            threadpool.shutdown();
        }
    }
//    public void printImage(String path) {
//        String image = "/9j/4AAQSkZJRgABAQEAYABgAAD/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAEAAAAAAAD/2wBDAAIBAQIBAQICAgICAgICAwUDAwMDAwYEBAMFBwYHBwcGBwcICQsJCAgKCAcHCg0KCgsMDAwMBwkODw0MDgsMDAz/2wBDAQICAgMDAwYDAwYMCAcIDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAz/wAARCAEfAhsDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD9/KKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAoooo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pI08tcU6igAooooAKKKKACiiigAooooAKKKKACiiigAb7tV4LUbyzZLdiWJxViigAAxRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABQRmiigBuynFQe1FFAAF20UUUrAFFFFMAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigD/2Q==";
//
//        try {
////            FileInputStream inputStream = openFileInput(file.getName());
////            BitmapFactory.Options options = new BitmapFactory.Options();
////            options.inScaled = false;
//            if (!path.isEmpty()) {
//                File externalDir = Environment.getExternalStorageDirectory();
//                String imagePath = externalDir.getPath() + path;
//                File file = new File(imagePath);
//                if (file != null) {
//                    image = fileToBase64(file);
//                }
//            }
//            Bitmap bmp = decodeBase64(image);
//            if (bmp == null) {
//                Log.e(TAG, "resource decoding is failed");
//                return;
//            }
//            byte[] data = WoosimImage.printBitmap(0, 0, 500, 300, bmp);
//            bmp.recycle();
//
//            sendData(WoosimCmd.setPageMode());
//            sendData(data);
//            sendData(WoosimCmd.PM_setStdMode());
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    public void printText(String string) {
        byte[] text = null;
        try {
            if (string == null)
                return;
            else {
                try {
                    text = string.getBytes("GBK");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            byte[] command = new byte[]{0x1B, 0x4D, 0x06};
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byteStream.write(WoosimCmd.setTextStyle(mEmphasis, mUnderline, false, mCharsize, mCharsize));
            byteStream.write(WoosimCmd.setTextAlign(mJustification));
            byteStream.write(WoosimCmd.setCodeTable(WoosimCmd.MCU_ARM, WoosimCmd.CT_DBCS, WoosimCmd.FONT_LARGE));
            byteStream.write(command);
            if (text != null) byteStream.write(text);
            byteStream.write(WoosimCmd.printData());

            sendData(WoosimCmd.initPrinter());
            sendData(byteStream.toByteArray());
        } catch (Exception e) {

        }
    }

    private final MyHandler mHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                activity.handleMessage(msg);
            }
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                String mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Printing in " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();

                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getInt(TOAST), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void processData2() {
        Intent intent = getIntent();
        action = intent.getStringExtra("ACTION_PRINT");
        value = intent.getStringExtra("TXT_TO_PRINT");
        arrArgs = intent.getStringArrayExtra("ARR_TO_PRINT");
        if (action == null) {
            action = "";
        }
        if (value == null) {
            value = "";
        }
        if (arrArgs == null) {
            arrArgs = new String[0];
        }
        if (!action.isEmpty()) {
            if (arrArgs.length > 0) {
                Log.d("Printer", "processData: " + arrArgs.length);
                presenter.processArray(arrArgs);
            } else if (!action.isEmpty() || !value.isEmpty()) {
                Log.d("Printer", "data: " + action + " - " + value);
                presenter.processArgument(action, value);
            }
        } else {
            arrArgs = intent.getStringArrayExtra("ONEIL_ARR_TO_PRINT");
            if (arrArgs == null) {
                arrArgs = new String[0];
            }
            if (arrArgs.length > 0) {
                presenter.processOneilData(arrArgs);
            } else {
                try {
                    Toast.makeText(getApplicationContext(), "Need to call from external application", Toast.LENGTH_SHORT).show();
                    Thread.sleep(500);
                    finishAffinity();
                } catch (Exception e) {

                }
            }
        }
    }

    public static String fileToBase64(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            fis.close();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        sp = getSharedPreferences(sp_file, Context.MODE_PRIVATE);
        presenter = new MainPresenter(this, sp);

        adapter = new AdapterDevice(this, listDevice);
        adapter.setClickListener((view, position) -> {
                    Toast.makeText(getBaseContext(), "Address selected", Toast.LENGTH_SHORT).show();
                    presenter.saveBluetoothAddress(adapter.getItem(position).getAddress());
                }
        );
        Log.d("Printer", "onCreate: ");
        mPrintService = new BluetoothPrintService(mHandler);
        mWoosim = new WoosimService(mHandler);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 2108);
        } else {
            registerAddress();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2108) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                registerAddress();
            } else {
                try {
                    Thread.sleep(300);
                    closeActivity(false, "Image permission is rejected");
                } catch (Exception e) {

                }
                // Permission is denied
            }
        }
    }

    private void connectDevice(String address) {
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mPrintService.connect(device, false);
    }

    private void registerAddress() {
        mac = sp.getString(sp_mac, "");
        if (mac.isEmpty()) {
            //create adapter
            binding.listDevice.setLayoutManager(new LinearLayoutManager(this));
            binding.listDevice.setAdapter(adapter);
            checkActivateBluetooth();
        } else {
            processData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPrintService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mPrintService.getState() == BluetoothPrintService.STATE_NONE) {
                // Start the Bluetooth print services
                mPrintService.start();
            }
        }
    }

    private void checkActivateBluetooth() {
        if (!BluetoothUtils.isEnable()) {
            BluetoothUtils.openBluetooth(this, isOn -> {
                if (isOn) {
                    presenter.getBluetoothDevice();
                } else {
                    Toast.makeText(getApplicationContext(), "Failed to activate bluetooth", Toast.LENGTH_SHORT).show();
                    finishAffinity();
                }
            });
        }
        presenter.getBluetoothDevice();
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        if (mPrintService != null) {
            mPrintService.stop();
        }
        super.onDestroy();
    }

    @Override
    public void showLoading() {
        Log.d("Printer", "showLoading: ");
//        binding.loading.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        binding.loading.setVisibility(View.GONE);
    }


    @Override
    public void isConnected(boolean bool) {
        Toast.makeText(getApplicationContext(), "isConnected " + bool, Toast.LENGTH_SHORT).show();
        Intent data = new Intent();
        data.putExtra("isSuccess", bool);
        data.putExtra("message", "");
        setResult(RESULT_OK, data);
        finishAffinity();
    }

    @Override
    public void closeActivity(boolean bool, String message) {
//        Intent data = new Intent();
//        data.putExtra("isSuccess", bool);
//        data.putExtra("message", message);
//        setResult(RESULT_OK, data);
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        this.finishAffinity();
//        System.exit(0);
    }

    @Override
    public void showError(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onComplete() {
        finishAffinity();
//        System.exit(0);
    }

    @Override
    public void showBluetoothData(List<BluetoothDevice> devices) {
        if (!devices.isEmpty()) {
            adapter.setData(devices);
            binding.loading.setVisibility(View.GONE);
            binding.data.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(getApplicationContext(), "No Bluetooth Found", Toast.LENGTH_SHORT).show();
            finishAffinity();
        }

    }

    @Override
    public Bitmap getAssetData(String fileName) {
        AssetManager assetManager = getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(fileName);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
            Log.d("Err", e.getMessage());
        }

        return bitmap;
    }

    // Debugging
    private static final String TAG = "MainActivity";
    private static final boolean D = true;

    // Message types sent from the BluetoothPrintService Handler
    public static final int MESSAGE_DEVICE_NAME = 1;
    public static final int MESSAGE_TOAST = 2;
    public static final int MESSAGE_READ = 3;

    // Key names received from the BluetoothPrintService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    private static final int PERMISSION_DEVICE_SCAN_SECURE = 11;
    private static final int PERMISSION_DEVICE_SCAN_INSECURE = 12;

    // Layout Views
    private boolean mEmphasis = false;
    private boolean mUnderline = false;
    private int mCharsize = 1;
    private int mJustification = WoosimCmd.ALIGN_LEFT;
    private TextView mTrack1View;
    private TextView mTrack2View;
    private TextView mTrack3View;
    private Menu mMenu = null;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the print services
    private BluetoothPrintService mPrintService = null;
    private WoosimService mWoosim = null;
}