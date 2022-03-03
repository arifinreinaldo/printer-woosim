package net.simplr.woosimdp230l;


import com.dascom.print.Logger;

import java.io.Closeable;
import java.io.IOException;

class Counter<V extends Closeable> {
    private V object;
    private int count = 0;

    Counter() {
    }

    public void setObject(V object) {
        if (this.object != null) {
            try {
                this.object.close();
            } catch (IOException var3) {
                var3.printStackTrace();
            }
        }

        this.object = object;
        ++this.count;
    }

    public V getObject() {
        return this.object;
    }

    public int addCount() {
        return ++this.count;
    }

    public int minusCount() {
        if (this.count > 0) {
            --this.count;
        }

        if (this.count == 0) {
            try {
                this.object.close();
                Logger.d("Counter", "close");
            } catch (IOException var2) {
                var2.printStackTrace();
            }
        }

        return this.count;
    }
}

