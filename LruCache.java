
import java.util.HashMap;

public class LruCache<K,V> {
    private static class Entry<K,V> {
        final K key;
        V value;
        Entry<K,V> prev;
        Entry<K,V> next;
        List<K,V> list;

        Entry(K key) {
            this.key = key;
        }

        void unlink() {
            assert prev != null;
            assert next != null;
            assert prev != this;
            assert next != this;
            prev.next = next;
            next.prev = prev;
            prev = null;
            next = null;
            --list.size;
            list = null;
        }
    };

    private static class List<K,V> extends Entry<K,V> {
        int size;

        List() {
            super(null);
            prev = next = this;
        }

        void clear() {
            while (next != this) {
                next.unlink();
            }
        }
        
        void append(Entry<K,V> e) {
            assert e.next == null;
            assert e.prev == null;
            e.prev = prev;
            e.next = this;
            prev.next = e;
            prev = e;
            e.list = this;
            ++size;
        }

        Entry<K,V> first() {
            assert next != this;
            return next;
        }
    }

    private final HashMap<K, Entry<K,V>> entries = new HashMap<>();
    private final List<K,V> singleUse = new List<K,V>();
    private final List<K,V> multiUse = new List<K,V>();
    private final int maxSize;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
        singleUse.clear();
        multiUse.clear();
    }

    public void put(K key, V value) {
        Entry<K,V> e = entries.get(key);
        if (e == null) {
            if (entries.size() == maxSize) {
                Entry<K,V> victim = singleUse.size >= maxSize / 4 ?
                                        singleUse.first() : multiUse.first();
                entries.remove(victim.key);
                victim.unlink();
            }
            e = new Entry<K,V>(key);
            entries.put(key, e);
            singleUse.append(e);
        } else {
            e.unlink();
            multiUse.append(e);
        }
        e.value = value;
    }

    public V get(K key) {
        Entry<K,V> e = entries.get(key);
        if (e != null) {
            e.unlink();
            multiUse.append(e);
            return e.value;
        }
        return null;
    }

    public V remove(K key) {
        Entry<K,V> e = entries.remove(key);
        if (e != null) {
            e.unlink();
            return e.value;
        }
        return null;
    }

    public static void main(String[] args) {
        LruCache<Integer, Integer> cache = new LruCache<>(2);
        
        assert cache.size() == 0;
        assert cache.get(1) == null;
        cache.put(1, 1);
        assert cache.size() == 1;
        assert cache.get(1) == 1;
        cache.put(2, 2);
        assert cache.size() == 2;
        assert cache.get(1) == 1;
        cache.put(3, 3);
        assert cache.get(2) == null;
        assert cache.get(1) == 1;
        assert cache.size() == 2;
        assert cache.remove(1) == 1;
        assert cache.get(1) == null;
        assert cache.size() == 1;
        assert cache.get(3) == 3;
        cache.clear();
        assert cache.size() == 0;
        assert cache.get(3) == null;
    }
}