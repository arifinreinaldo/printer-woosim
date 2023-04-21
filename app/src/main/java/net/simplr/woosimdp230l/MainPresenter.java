package net.simplr.woosimdp230l;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.util.Log;

import com.dascom.print.ZPL;
import com.dascom.print.utils.BluetoothUtils;
import com.woosim.printer.WoosimCmd;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import honeywell.connection.ConnectionBase;
import honeywell.connection.Connection_Bluetooth;
import honeywell.printer.DocumentEZ;
import honeywell.printer.DocumentLP;

public class MainPresenter {
    String TAG = "Dascom";
    private BluetoothCustom mIConnection;
    private ZPL zpl;
    private View view;
    private SharedPreferences spData;
    private
    SharedPreferences.Editor editor;
    private final String sp_rec = "recordPrint";
    String oldRecord = "";
    final String separator = "@@";
    ArrayList<String> records = new ArrayList<>();
    private final String sp_mac = "macaddress";


    String paramMAC = "";
    String receiptNo = "";
    String receiptDate = "";
    String qty = "";
    String uom = "";
    String lotNo = "";
    String itemCode = "";
    String itemName = "";
    String itemBarcode = "";
    String palletNo = "";
    int index = 0;
    String mac = "";
    int retry = 0;

    MainPresenter(View view, SharedPreferences sp) {
        this.view = view;
        spData = sp;
        editor = spData.edit();
    }

    void onDestroy() {

    }

    void addSinglePrint(String record) {
        records.add(record);
        doPrint();
    }

    void doPrint() {
        index = 0;
        Log.d(TAG, "doPrint: " + records.size());
        if (records.size() > 0) {
            String first = records.get(0);
            mac = first.split(";")[0].trim();
            String savedMac = spData.getString(sp_mac, "");
            mac = savedMac;
            try {
                ExecutorService threadpool = Executors.newCachedThreadPool();
                Future<Boolean> futureTask = threadpool.submit(() -> connectBluetoothNative(mac));

                while (!futureTask.isDone()) {
                    System.out.println("FutureTask is not finished yet...");
                }
                Boolean result = futureTask.get();
                if (result) {
                    doRecursivePrintingNative();
                } else {
                    view.closeActivity(false, "Failed to Connect");
                }
                threadpool.shutdown();
            } catch (Exception e) {
                view.closeActivity(false, e.getMessage());
            }
        }
    }

    private static BluetoothAdapter getAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    private static BluetoothDevice getBluetoothDevice(String mac) {
        BluetoothAdapter adapter = getAdapter();
        String tempMac = mac.toUpperCase();
        return adapter != null && BluetoothAdapter.checkBluetoothAddress(tempMac) ? adapter.getRemoteDevice(tempMac) : null;
    }

    Boolean connectBluetoothNative(String address) {
        retry++;
        mIConnection = new BluetoothCustom(getBluetoothDevice(address));
        return mIConnection.connect();
    }

    void doRecursivePrintingNative() {
        try {
            String value = records.get(index);
            Log.d(TAG, "doRecursivePrinting: " + value);
            final byte[] cmd_print = WoosimCmd.printData();
            byte[] valByte = value.getBytes("windows-874");
            System.out.println("printText value : " + value + " -- " + value.getBytes().length);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(512);
            byteStream.write(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_WIN874, WoosimCmd.FONT_LARGE));
            byteStream.write(valByte);
            byteStream.write(cmd_print);

            mIConnection.send(WoosimCmd.initPrinter(), 0, 0);
            mIConnection.send(byteStream.toByteArray(), 0, 0);
            Thread.sleep(100);
            index++;
            if (index < records.size()) {
                doRecursivePrintingNative();
            } else {
                editor.putString(sp_rec, "");
                editor.apply();
                records.clear();
                doDisconnect();
            }
        } catch (Exception e) {
            view.closeActivity(false, e.getMessage());
        }
    }

    public void doDisconnect() {
        try {
            mIConnection.disconnect();
            view.closeActivity(true, "Done Print");
        } catch (Exception e) {
            view.closeActivity(false, e.getMessage());
        }
    }

    public void clearRecord() {
        editor.putString(sp_rec, "");
        editor.apply();
    }

    public void addRecord(String value) {
        records.add(value);
    }

    public void doPrintAll() {
        if (records.size() == 0) {
            view.closeActivity(false, "No Data to Print");
        } else {
            doPrint();
        }

    }

    public void processArray(String[] values) {
        for (int x = 0; x < values.length; x++) {
            addRecord(values[x]);
        }
        doPrintAll();
    }

    public void processArgument(String action, String value) {
        if (action.equalsIgnoreCase("SinglePrint")) {
            if (value != null) {
                addSinglePrint(value);
            }
        } else {
            if (action.equalsIgnoreCase("startPrint")) {
                clearRecord();
            } else if (action.equalsIgnoreCase("addPrintRecord")) {
                addRecord(value);
            } else if (action.equalsIgnoreCase("doPrint")) {
                doPrintAll();
            }
        }
    }

    public void saveBluetoothAddress(String address) {
        try {
            editor.putString(sp_mac, address);
            editor.apply();
            Thread.sleep(100);
        } catch (Exception e) {

        } finally {
            view.onComplete();
        }
    }

    public void getBluetoothDevice() {
        try {
            view.showLoading();
            Set<BluetoothDevice> data = BluetoothUtils.getBondedDevices();
            view.showBluetoothData(new ArrayList<>(data));
            view.hideLoading();
        } catch (Exception e) {

        }
    }

    public void processOneilData(String[] arrArgs) {
        Long start = System.currentTimeMillis();
        byte[] printData = {0};
        DocumentEZ docEZ = new DocumentEZ("MF185");
        DocumentLP docLP = new DocumentLP("!");
        //=============GENERATING RECEIPT====================================//
        try {
            //Record:TEXT:kalimat nya:TEXT:X:TEXT:Y
            String savedMac = spData.getString(sp_mac, "");
            ConnectionBase conn = Connection_Bluetooth.createClient(savedMac, false);
            for (int i = 0; i < arrArgs.length; i++) {
                String record = arrArgs[i];
                if (record.startsWith("Image")) {
                    String recordData[] = record.split(":IMAGE:");
                    Bitmap bmp = view.getAssetData(recordData[1]);
                    if (bmp != null) {
                        try {
                            docLP.writeImage(bmp, 832);
                        } catch (Exception e) {
                            Log.d(TAG, "processOneilData: ");
                        }
                        printData = docLP.getDocumentData();
                        if (!conn.getIsOpen()) {
                            conn.open();
                        }
                        int bytesWritten = 0;
                        int bytesToWrite = 1024;
                        int totalBytes = printData.length;
                        int remainingBytes = totalBytes;
                        while (bytesWritten < totalBytes) {
                            if (remainingBytes < bytesToWrite)
                                bytesToWrite = remainingBytes;
                            //Send data, 1024 bytes at a time until all data sent
                            conn.write(printData, bytesWritten, bytesToWrite);
                            bytesWritten += bytesToWrite;
                            remainingBytes = remainingBytes - bytesToWrite;
                            Thread.sleep(100);
                        }
                    }
                } else if (record.startsWith("Record")) {
                    String recordData[] = record.split(":TEXT:");
                    double xDouble = Double.parseDouble(recordData[2]);
                    double yDouble = Double.parseDouble(recordData[3]);
                    int x = (int) Math.floor(xDouble);
                    int y = (int) Math.floor(yDouble);
                    docEZ.writeText(recordData[1], x, y);
                }
            }
            printData = docEZ.getDocumentData();
            if (!conn.getIsOpen()) {
                conn.open();
            }
            int bytesWritten = 0;
            int bytesToWrite = 1024;
            int totalBytes = printData.length;
            int remainingBytes = totalBytes;
            while (bytesWritten < totalBytes) {
                if (remainingBytes < bytesToWrite)
                    bytesToWrite = remainingBytes;

                //Send data, 1024 bytes at a time until all data sent
                conn.write(printData, bytesWritten, bytesToWrite);
                bytesWritten += bytesToWrite;
                remainingBytes = remainingBytes - bytesToWrite;
                Thread.sleep(100);
            }

            //signals to close connection
            conn.close();
            Long end = System.currentTimeMillis();
            Log.d(TAG, "processOneilData: " + (end - start));
            view.closeActivity(true, "Done Print");
        } catch (Exception e) {
            view.showError(e.getMessage());
        }
    }

    interface View {
        void showLoading();

        void hideLoading();

        void isConnected(boolean bool);

        void closeActivity(boolean bool, String message);

        void showError(String message);

        void onComplete();

        void showBluetoothData(List<BluetoothDevice> devices);

        Bitmap getAssetData(String fileName);
    }
}
