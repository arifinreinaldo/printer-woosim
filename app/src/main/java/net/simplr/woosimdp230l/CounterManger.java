package net.simplr.woosimdp230l;

import java.io.Closeable;
import java.util.HashMap;

class CounterManger<V extends Closeable> {
    private HashMap<String, Counter<V>> map = new HashMap(20);

    CounterManger() {
    }

    public void add(String key) {
        Counter<V> counter = (Counter) this.map.get(key);
        if (counter != null) {
            counter.addCount();
        }

    }

    public void minus(String key) {
        Counter<V> counter = (Counter) this.map.get(key);
        if (counter != null && counter.minusCount() == 0) {
            this.map.remove(key);
        }

    }

    public Counter<V> getAndCreate(String key) {
        Counter<V> counter = (Counter) this.map.get(key);
        if (counter == null) {
            counter = new Counter();
            this.map.put(key, counter);
        }

        return counter;
    }
}
