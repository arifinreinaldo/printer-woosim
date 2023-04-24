package net.simplr.woosimdp230l.base;

import android.app.Application;

import net.simplr.woosimdp230l.sunmi.SunmiPrintHelper;

public class BaseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }

    /**
     * Connect print service through interface library
     */
    private void init() {
        SunmiPrintHelper.getInstance().initSunmiPrinterService(this);
    }
}

