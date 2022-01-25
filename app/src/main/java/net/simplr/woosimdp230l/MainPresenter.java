package net.simplr.woosimdp230l;

import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;

import com.dascom.print.ZPL;
import com.dascom.print.connection.BluetoothConnection;
import com.dascom.print.connection.IConnection;
import com.dascom.print.utils.BluetoothUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.observers.DisposableCompletableObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainPresenter {
    private IConnection mIConnection;
    private ZPL zpl;
    private View view;
    private final CompositeDisposable disposables = new CompositeDisposable();
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

    MainPresenter(View view, SharedPreferences sp) {
        this.view = view;
        spData = sp;
        editor = spData.edit();
    }

    void onDestroy() {
        disposables.dispose();
    }

    void addSinglePrint(String record) {
        records.add(record);
        doPrint();
    }

    void doPrint() {
        index = 0;
        if (records.size() > 0) {
            String first = records.get(0);
            String mac = first.split(";")[0].trim();
            String savedMac = spData.getString(sp_rec, "");
            if (!savedMac.isEmpty() && mac.isEmpty()) {
                mac = savedMac;
            }
            disposables.add(
                    connectBluetooth(mac).subscribe(aBoolean -> {
                        if (aBoolean) {
//                            for (int x = 0; x < records.size(); x++) {
//                                splitRecord(records.get(x));
//                                printLSHReceipt(receiptNo, receiptDate, qty, uom, lotNo, itemCode, itemName, itemBarcode, palletNo);
//                                Thread.sleep(100);
//                            }
//                            doDisconnect();
                            doRecursivePrinting();
                        } else {
                            view.closeActivity(false, "Failed to Connect");
                        }
                    }, throwable -> {
                        view.closeActivity(false, throwable.getMessage());
                    })
            );
        }
    }

    void doRecursivePrinting() {
        Completable.fromAction(() -> {
            splitRecord(records.get(index));
            printLSHReceipt(receiptNo, receiptDate, qty, uom, lotNo, itemCode, itemName, itemBarcode, palletNo);
        }).delay(100, TimeUnit.MILLISECONDS).subscribeWith(new DisposableCompletableObserver() {
            @Override
            public void onComplete() {
                index++;
                if (index < records.size()) {
                    doRecursivePrinting();
                } else {
                    editor.putString(sp_rec, "");
                    editor.apply();
                    doDisconnect();
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }
        });
    }

    void splitRecord(String value) {
        String[] arr = value.split(";");
        paramMAC = arr[0].trim();
        receiptNo = arr[1].trim();
        receiptDate = arr[2].trim();
        qty = arr[3].trim();
        uom = arr[4].trim();
        lotNo = arr[5].trim();
        itemCode = arr[6].trim();
        itemName = arr[7].trim();
        itemBarcode = arr[8].trim();
        palletNo = arr[9].trim();
    }

    Observable<Boolean> connectBluetooth(String address) {
        mIConnection = new BluetoothConnection(BluetoothUtils.getBluetoothDevice(address));
        return Observable.just(mIConnection.connect()).
                subscribeOn(Schedulers.io()).
                delay(1, TimeUnit.SECONDS).
                doOnSubscribe(disposable -> view.showLoading()).
                observeOn(AndroidSchedulers.mainThread()).
                doFinally(() -> view.hideLoading());
    }

    /* External call is using this function blocks*/
    public void printLSHReceipt(String receiptNo, String receiptDate, String qty, String uom,
                                String lotNo, String itemCode, String itemName, String itemBarcode, String palletNo) {
        try {
            initiateZPL(3.1, 3.1);

            //3 inch: 609 * 609
            printZPLText(10, 20, 2, "Receive No: " + receiptNo);
            printZPLText(10, 80, 2, "Receive Date: " + receiptDate);
            printZPLText(10, 140, 2, "Qty: " + qty);
            printZPLText(180, 140, 2, "UOM: " + uom);
            if (itemBarcode.equals("")) {
                printZPLQRCode(380, 20, 7, itemCode);
                System.out.println("printZPLQRCode itemCode " + itemCode);
            } else {
                printZPLQRCode(380, 20, 7, itemBarcode);
                System.out.println("printZPLQRCode itemBarcode " + itemBarcode);
            }
            printZPLHLine(10, 200, 609, 1);
            printZPLText(10, 220, 2, "Item No: " + itemCode);
            if (itemName.length() <= 46) {
                printZPLText(10, 280, 1, itemName);
            } else {
                String firstLine = itemName.substring(0, 46);
                String secondLine = itemName.substring(46);
                printZPLText(10, 280, 1, firstLine);
                printZPLText(10, 320, 1, secondLine);
            }
            printZPLHLine(10, 380, 609, 1);
            printZPLText(200, 420, 2, "Pallet No: " + palletNo);
            printZPLText(200, 480, 2, "Lot No: " + lotNo);
            printZPLQRCode(10, 420, 7, palletNo);
//            System.out.println("printZPLQRCode palletNo " + palletNo);
//
            triggerZPL();
        } catch (Exception e) {
            view.showError("print LSH receipt : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void initiateZPL(double lengthInch, double widthInch) {
        if (lengthInch == 0) {
            lengthInch = 3 * 203;
        } else {
            lengthInch = lengthInch * 203;
        }

        if (widthInch == 0) {
            widthInch = 3 * 203;
        } else {
            widthInch = widthInch * 203;
        }

        System.out.println("lengthInch : " + lengthInch + " -- widhtInch : " + widthInch);

        final int fLengthInch = (int) lengthInch;
        final int fWidthtInch = (int) widthInch;

        zpl = new ZPL(mIConnection);
        zpl.setLabelStart();

        zpl.setLabelLength(fLengthInch);   //1 inch = 203 dot
        zpl.setLabelWidth(fWidthtInch);   //1 inch = 203 dot
    }

    public void printZPLHLine(int xPos, int yPos, int length, int thickness) {
        zpl.printHLine(xPos, yPos, length, thickness);
        System.out.println("printZPLHLine " + length);
    }

    public void printZPLText(int xPos, int yPos, double style, String text) {
        if (!text.trim().equals("")) {
            zpl.printText(xPos, yPos, style, 1, text);
            System.out.println("printZPLText " + text);
        }
    }

    public void printZPLBarCode(int xPos, int yPos, int height, String text) {
        //zpl.printCode128(xPos, yPos, height, heightHumanRead, widthHumanRead, flagHumanRead, posHumanRead, text);
        if (!text.trim().equals("")) {
            zpl.printCode128(xPos, yPos, height, true, true, text);
            System.out.println("printZPLBarCode " + text);
        }
    }

    public void printZPLQRCode(int xPos, int yPos, int size, String text) {
        if (!text.trim().equals("")) {
            zpl.printQRCode(xPos, yPos, size, 'L', text);
            System.out.println("printZPLQRCode " + text);
        }
    }

    public void triggerZPL() {
        if (zpl != null) {
            zpl.setLabelEnd();
        }
    }

    public void doDisconnect() {
        if (mIConnection.isConnected()) {
            disposables.add(disconnectBluetooth().subscribeWith(new DisposableCompletableObserver() {
                @Override
                public void onComplete() {
                    view.closeActivity(true, "Done Print");
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    view.closeActivity(false, e.getMessage());
                }
            }));
        }
    }

    Completable disconnectBluetooth() {
        return Completable.fromAction(() -> mIConnection.disconnect());
    }

    public void doConnect(String address) {
        disposables.add(
                connectBluetooth(address).subscribe(aBoolean -> {
                    view.isConnected(aBoolean);
                }, throwable -> {
                    view.closeActivity(false, throwable.getMessage());
                })
        );
    }

    public void clearRecord() {
        Completable.fromAction(() -> {
            editor.putString(sp_rec, "");
            editor.apply();
        }).subscribeWith(new DisposableCompletableObserver() {
            @Override
            public void onComplete() {
                view.onComplete();
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }
        });
    }

    public void addRecord(String value) {
        Completable.fromAction(() -> {
            oldRecord = spData.getString(sp_rec, "");
            final String newRecord = oldRecord + separator + value;
            editor.putString(sp_rec, newRecord);
            editor.apply();
        }).delay(200, TimeUnit.MILLISECONDS).subscribeWith(new DisposableCompletableObserver() {
            @Override
            public void onComplete() {
                view.onComplete();
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }
        });
    }

    public void doPrintAll() {
        oldRecord = spData.getString(sp_rec, "");
        List<String> row = Arrays.asList(oldRecord.split(separator));
        records.clear();
        for (int x = 0; x < row.size(); x++) {
            if (!row.get(x).isEmpty()) {
                records.add(row.get(x));
            }
        }
        if (records.size() == 0) {
            view.closeActivity(false, "No Data to Print");
        } else {
            doPrint();
        }

    }

    public void processArgument(String action, String value) {
        if (value == null) {
            value = "";
        }
        if (action != null) {
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
    }

    public void saveBluetoothAddress(String address) {
        Completable.fromAction(() -> {
            editor.putString(sp_mac, address);
            editor.apply();
        }).delay(100, TimeUnit.MILLISECONDS).subscribeWith(new DisposableCompletableObserver() {
            @Override
            public void onComplete() {
                view.onComplete();
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }
        });
    }

    Observable<Set<BluetoothDevice>> observeBluetoothDevice() {
        return Observable.just(BluetoothUtils.getBondedDevices()).
                subscribeOn(Schedulers.io()).
                delay(1, TimeUnit.SECONDS).
                doOnSubscribe(disposable -> view.showLoading()).
                observeOn(AndroidSchedulers.mainThread()).
                doFinally(() -> view.hideLoading());
    }

    public void getBluetoothDevice() {
        disposables.add(observeBluetoothDevice().subscribe(bluetoothDevices -> {
            ArrayList<Device> listDevice = new ArrayList<>();
            for (int x = 0; x < 100; x++) {
                for (BluetoothDevice device : bluetoothDevices) {
                    listDevice.add(new Device(device.getName(), device.getAddress()));
                }
            }
            view.showBluetoothData(listDevice);
        }, throwable -> {
            view.closeActivity(false, throwable.getMessage());
        }));
    }

    interface View {
        void showLoading();

        void hideLoading();

        void isConnected(boolean bool);

        void closeActivity(boolean bool, String message);

        void showError(String message);

        void onComplete();

        void showBluetoothData(List<Device> devices);

    }
}
