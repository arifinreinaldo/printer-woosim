package net.simplr.woosimdp230l;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.util.Log;

import com.dascom.print.ZPL;
import com.dascom.print.utils.BluetoothUtils;
import com.woosim.printer.WoosimCmd;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    interface View {
        void showLoading();

        void hideLoading();

        void isConnected(boolean bool);

        void closeActivity(boolean bool, String message);

        void showError(String message);

        void onComplete();

        void showBluetoothData(List<BluetoothDevice> devices);

    }
}
