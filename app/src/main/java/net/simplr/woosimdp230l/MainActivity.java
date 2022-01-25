package net.simplr.woosimdp230l;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dascom.print.connection.BluetoothConnection;
import com.dascom.print.utils.BluetoothUtils;

import net.simplr.woosimdp230l.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements MainPresenter.View {
    SharedPreferences sp;
    private final String sp_file = "woosimdp230lmac";
    private final String sp_mac = "macaddress";
    private String mac;
    private MainPresenter presenter;
    ActivityMainBinding binding;
    BluetoothConnection bluetoothConnection;
    AdapterDevice adapter;
    List<Device> listDevice = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        sp = getSharedPreferences(sp_file, Context.MODE_PRIVATE);
        presenter = new MainPresenter(this, sp);

        String action = getIntent().getStringExtra("ACTION_PRINT");
        String value = getIntent().getStringExtra("TXT_TO_PRINT");
        presenter.processArgument(action, value);
        adapter = new AdapterDevice(this, listDevice);
        adapter.setClickListener((view, position) -> {
                    Toast.makeText(getBaseContext(), "Address selected", Toast.LENGTH_SHORT).show();
                    presenter.saveBluetoothAddress(adapter.getItem(position).getAddress());
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        mac = sp.getString(sp_mac, "");
        if (mac.isEmpty()) {
            //create adapter
            binding.listDevice.setLayoutManager(new LinearLayoutManager(this));
            binding.listDevice.setAdapter(adapter);
            checkActivateBluetooth();
        } else {
            Toast.makeText(getApplicationContext(), "Need to call from external application", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void checkActivateBluetooth() {
        if (!BluetoothUtils.isEnable()) {
            BluetoothUtils.openBluetooth(this, isOn -> {
                if (isOn) {
                    presenter.getBluetoothDevice();
                } else {
                    Toast.makeText(getApplicationContext(), "Failed to activate bluetooth", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }
        presenter.getBluetoothDevice();
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void showLoading() {
        binding.loading.setVisibility(View.VISIBLE);
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
        finish();
    }

    @Override
    public void closeActivity(boolean bool, String message) {
//        Intent data = new Intent();
//        data.putExtra("isSuccess", bool);
//        data.putExtra("message", message);
//        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onComplete() {
        finish();
    }

    @Override
    public void showBluetoothData(List<Device> devices) {
        if (!devices.isEmpty()) {
            adapter.setData(devices);
            binding.loading.setVisibility(View.GONE);
        } else {
            Toast.makeText(getApplicationContext(), "No Bluetooth Found", Toast.LENGTH_SHORT).show();
            finish();
        }

    }
}