package net.simplr.woosimdp230l;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.dascom.print.Logger;
import com.dascom.print.connection.ISocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothCustom implements ISocket {
    private static final String TAG = "BluetoothConnection";
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final CounterManger<BluetoothSocket> manager = new CounterManger();
    private static Object lock = new Object();
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private BluetoothDevice btDevice;
    private boolean fromCache;
    private int retryCount;

    public BluetoothCustom(BluetoothDevice btDevice) {
        this(btDevice, false);
    }

    public BluetoothCustom(BluetoothDevice btDevice, boolean fromCache) {
        this.retryCount = 2;
        Logger.i("BluetoothConnection", "create: " + this);
        this.btDevice = btDevice;
        this.fromCache = fromCache;
    }

    public boolean connect() {
        if (this.btDevice != null) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            adapter.cancelDiscovery();
            synchronized (manager) {
                try {
                    String key = this.btDevice.getAddress();
                    Counter<BluetoothSocket> counter = manager.getAndCreate(key);
                    this.btSocket = (BluetoothSocket) counter.getObject();
                    boolean var10000;
                    if (this.btSocket != null) {
                        if (this.fromCache && this.btSocket.isConnected()) {
                            manager.add(key);
                            this.inputStream = this.btSocket.getInputStream();
                            this.outputStream = this.btSocket.getOutputStream();
                            var10000 = true;
                            return var10000;
                        }

                        manager.minus(key);
                    }

                    if (this.btDevice.getBondState() != 12) {
                        boolean bond = this.btDevice.createBond();
                        if (bond) {
                            int i = 0;

                            while (this.btDevice.getBondState() != 12) {
                                try {
                                    Thread.sleep(50L);
                                    if (i == 400) {
                                        break;
                                    }

                                    ++i;
                                } catch (InterruptedException var9) {
                                    var9.printStackTrace();
                                }
                            }
                        }
                    }

                    this.btSocket = this.btDevice.createRfcommSocketToServiceRecord(uuid);
                    int i = 0;

                    while (i < this.retryCount) {
                        try {
                            this.btSocket.connect();
                            this.inputStream = this.btSocket.getInputStream();
                            this.outputStream = this.btSocket.getOutputStream();
                            counter.setObject(this.btSocket);
                            var10000 = true;
                            return var10000;
                        } catch (Exception var10) {
                            var10.printStackTrace();
                            ++i;
                        }
                    }

                    return false;
                } catch (IOException var11) {
                    Logger.e("BluetoothConnection", "connection failure", var11);
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    protected void finalize() throws Throwable {
        try {
            Logger.i("BluetoothConnection", "finalize: " + this);
            this.disconnect();
        } finally {
            super.finalize();
        }

    }

    public void flush() {
        if (this.btSocket != null) {
            try {
                this.btSocket.getOutputStream().flush();
            } catch (IOException var2) {
                var2.printStackTrace();
            }
        }

    }

    public void disconnect() {
        if (this.btSocket != null && this.btSocket.isConnected()) {
            Logger.i("BluetoothConnection", "disconnect");
            synchronized (manager) {
                manager.minus(this.btSocket.getRemoteDevice().getAddress());
            }
        }

        this.btSocket = null;
    }

    public boolean isConnected() {
        return this.btSocket != null && this.btSocket.isConnected();
    }

    public Object getLock() {
        return lock;
    }

    public void setReconnectCount(int count) {
        this.retryCount = count;
    }

    public int send(byte[] data, int offset, int length) {
        if (this.btSocket != null) {
            try {
                this.outputStream.write(data);
                return length;
            } catch (IOException var5) {
                var5.printStackTrace();
            }
        }

        return -1;
    }

    public int read(byte[] buff, int offset, int length) {
        if (this.btSocket != null) {
            try {
                return this.inputStream.read(buff, offset, length);
            } catch (IOException var5) {
                var5.printStackTrace();
            }
        }

        return -1;
    }

    public String getAddress() {
        return this.btDevice.getAddress();
    }
}
