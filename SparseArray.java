
// https://github.com/jzebedee/rhbackshiftdict/blob/master/src/robinhood/RobinHoodDictionary.cs
// https://github.com/goossaert/hashmap/blob/master/backshift_hashmap.cc
// http://codecapsule.com/2013/11/17/robin-hood-hashing-backward-shift-deletion/

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class SparseArray<T> {
    private static final int MIN_CAPACITY = 16;
    private static final float MAX_LOAD_FACTOR = 0.85f;

    private boolean frozen;
    private int size;
    private int capacity;
    private long[] hashKeys; // hash in upper 32 bits, and key in lower
    private T[] values;

    public SparseArray() {
        resize(MIN_CAPACITY);
    }

    public SparseArray(final int initialCapacity) {
        resize(pow2Capacity(initialCapacity));
    }

    public SparseArray(final SparseArray<T> other) {
        int neededCapacity = pow2Capacity(other.size);
        if ((float)other.size / neededCapacity > MAX_LOAD_FACTOR) {
            neededCapacity *= 2;
        }
        resize(neededCapacity);
        putAll(this, other.size, other.hashKeys, other.values);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof SparseArray)) {
            return false;
        }
        final SparseArray other = (SparseArray)obj;
        if (other.size != size) {
            return false;
        }
        int found = 0;
        for (int i = 0; found < size; ++i) {
            final long hk = hashKeys[i];
            if ((int)(hk >> 32) != 0) {
                final Object a = get((int)hk);
                final Object b = other.get((int)hk);
                if (!a.equals(b)) {
                    return false;
                }
                ++found;
            }
        }
        return true;
    }

    public int[] keys() {
        final int[] keys = new int[size];
        int found = 0;
        for (int i = 0; found < size; ++i) {
            final long hk = hashKeys[i];
            if ((int)(hk >> 32) != 0) {
                keys[found++] = (int)hk;
            }
        }
        return keys;
    }

    public Iterable<T> values() {
        return new Iterable<T>()  {
            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    int index = 0;
                    int found = 0;

                    @Override
                    public boolean hasNext() {
                        return found < size;
                    }

                    @Override
                    public T next() {
                        for (; index < capacity; ++index) {
                            final T value = values[index];
                            if (value != null) {
                                ++index;
                                ++found;
                                return value;
                            }
                        }
                        throw new NoSuchElementException();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    public boolean containsKey(final int key) {
        return find(calcHashKey(key)) >= 0;
    }

    public T get(final int key) {
        final int index = find(calcHashKey(key));
        if (index >= 0) {
            return values[index];
        }
        return null;
    }

    public T get(final int key, final T defaultValue) {
        final int index = find(calcHashKey(key));
        if (index >= 0) {
            return values[index];
        }
        return defaultValue;
    }

    public void freeze() {
        frozen = true;
    }

    public void clear() {
        if (frozen) {
            throw new UnsupportedOperationException();
        }
        for (int i = 0; i < capacity; ++i) {
            hashKeys[i] = 0;
            values[i] = null;
        }
        size = 0;
    }

    public void put(final int key, final T value) {
        if (frozen) {
            throw new UnsupportedOperationException();
        }
        if ((float)size / capacity > MAX_LOAD_FACTOR) {
            resize(capacity * 2);
        }
        put(calcHashKey(key), value);
    }

    public boolean remove(final int key) {
        if (frozen) {
            throw new UnsupportedOperationException();
        }
        final int index = find(calcHashKey(key));
        if (index < 0) {
            return false;
        }

        for (int i = 0; i < capacity; ++i) {
            final int currIndex = (index + i) & (capacity - 1);
            final int nextIndex = (index + i + 1) & (capacity - 1);

            final int nextHash = (int)(hashKeys[nextIndex] >> 32);
            if (nextHash == 0 || distToStart(nextHash, nextIndex) == 0) {
                hashKeys[currIndex] = 0;
                values[currIndex] = null;
                --size;
                return true;
            }

            // swap currIndex and nextIndex
            final long tempHK = hashKeys[currIndex];
            final T tempVal = values[currIndex];
            hashKeys[currIndex] = hashKeys[nextIndex];
            values[currIndex] = values[nextIndex];
            hashKeys[nextIndex] = tempHK;
            values[nextIndex] = tempVal;
        }

        throw new RuntimeException("control flow should not get here");
    }

    private void put(long hashKey, T value) {
        final int startIndex = (int)(hashKey >> 32) & (capacity - 1);
        int probe = 0;

        for (int i = 0; i < capacity; ++i, ++probe) {
            final int index = (startIndex + i) & (capacity - 1);
            final long hk = hashKeys[index];
            final int h = (int)(hk >> 32);

            if (h == 0) {
                hashKeys[index] = hashKey;
                values[index] = value;
                ++size;
                return;
            }

            if (hk == hashKey) {
                values[index] = value;
                return;
            }

            final int p = distToStart(h, index);
            if (probe > p) {
                probe = p;
                long tempHK = hashKeys[index];
                T tempVal = values[index];
                hashKeys[index] = hashKey;
                values[index] = value;
                hashKey = tempHK;
                value = tempVal;
            }
        }

        throw new RuntimeException("control flow should not get here");
    }

    private int find(final long hashKey) {
        if (size == 0) {
            return -1;
        }
        final int startIndex = (int)(hashKey >> 32) & (capacity - 1);
        int probe = 0;

        for (int i = 0; i < capacity; ++i) {
            final int index = (startIndex + i) & (capacity - 1);
            final long hk = hashKeys[index];

            if (hk == hashKey) {
                return index;
            }

            final int h = (int)(hk >> 32);
            if (h != 0) {
                probe = distToStart(h, index);
            }

            if (i > probe) {
                break;
            }
        }

        return -1;
    }

    @SuppressWarnings("unchecked")
    private void resize(int newCapacity) {
        if (newCapacity < size) {
            throw new RuntimeException("new capacity is too small");
        }

        final int oldSize = size;
        final long[] oldHashKeys = hashKeys;
        final T[] oldValues = values;

        size = 0;
        capacity = newCapacity;
        hashKeys = new long[newCapacity];
        values = (T[]) new Object[newCapacity]; // unchecked cast

        putAll(this, oldSize, oldHashKeys, oldValues);
    }

    private static<T> void putAll(final SparseArray<T> arr, final int size, final long[] hashKeys, final T[] values) {
        int found = 0;
        for (int i = 0; found < size; ++i) {
            final long hk = hashKeys[i];
            if ((int)(hk >> 32) != 0) {
                arr.put(hk, values[i]);
                ++found;
            }
        }
    }

    private int distToStart(int hash, int indexStored) {
        final int startIndex = hash & (capacity - 1);
        if (startIndex <= indexStored) {
            return indexStored - startIndex;
        }
        return indexStored + (capacity - startIndex);
    }

    private static long calcHashKey(int key) {
        int hash = smear(key);
        if (hash == 0) {
            hash = 1;
        }
        return ((long)hash << 32) | (key & 0xFFFFFFFFL);
    }

    /*
     * This method was written by Doug Lea with assistance from members of JCP
     * JSR-166 Expert Group and released to the public domain, as explained at
     * http://creativecommons.org/licenses/publicdomain
     * 
     * As of 2010/06/11, this method is identical to the (package private) hash
     * method in OpenJDK 7's java.util.HashMap class.
     */
    private static int smear(int hashCode) {
        hashCode ^= (hashCode >>> 20) ^ (hashCode >>> 12);
        return hashCode ^ (hashCode >>> 7) ^ (hashCode >>> 4);
    }

    private static int pow2Capacity(int x) {
        if (x < MIN_CAPACITY) {
            return MIN_CAPACITY;
        }
        x = x - 1;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }



    public static void main(String[] args) {
        System.out.println("hei");
        testHashKeyPacking(0);
        testHashKeyPacking(1);
        testHashKeyPacking(-1);
        testHashKeyPacking(255);
        testHashKeyPacking(113254325);
        testHashKeyPacking(Integer.MIN_VALUE);
        testHashKeyPacking(Integer.MAX_VALUE);

        assert pow2Capacity(0) == 16;
        assert pow2Capacity(1) == 16;
        assert pow2Capacity(-1) == 16;
        assert pow2Capacity(10) == 16;
        assert pow2Capacity(15) == 16;
        assert pow2Capacity(16) == 16;
        assert pow2Capacity(129) == 256;
        assert pow2Capacity(255) == 256;
        assert pow2Capacity(256) == 256;

        SparseArray<Integer> arr = new SparseArray<>();
        final int N = 100000;
        for (int i = 0; i < N; ++i) {
            arr.put(i, i);
            arr.put(i, i);
        }
        
        int[] keys = arr.keys();
        assert keys.length == N;
        for (int i = 0; i < N; ++i) {
            assert arr.get(i) == i;
            assert arr.get(keys[i]) == keys[i];
        }
        
        int valueCount = 0;
        SparseArray<Integer> arr2 = new SparseArray<>(N);
        for (Integer val : arr.values()) {
            int i = val;
            assert arr2.get(i) == null;
            arr2.put(i, i);
            assert arr2.get(i) == i;
            assert arr.get(i) == i;
            ++valueCount;
        }

        SparseArray<Integer> arr3 = new SparseArray<>(arr);
        assert arr.equals(arr3);

        assert arr.equals(arr2);
        assert valueCount == N;
        assert arr.size() == N;
        assert arr.containsKey(100);
        assert arr.remove(100);
        assert !arr.remove(100);
        assert !arr.containsKey(100);
        assert arr.size() == N-1;
        assert !arr.equals(arr2);

        for (int i = 0; i < N; ++i) {
            if (i != 100) {
                assert arr.get(i) == i;
            }
        }
        for (int i = 0; i < N; ++i) {
            if (i != 100) {
                assert arr.remove(i);
            }
        }
        assert arr.size() == 0;
        for (int i = 0; i < N; ++i) {
            assert arr.get(i) == null;
        }

        final long COUNT = 10000000;

        long start2 = System.currentTimeMillis();
        SparseArray<Integer> sparseArray = new SparseArray<>(16);
        for (int i = 0; i < COUNT; ++i) {
            sparseArray.put(i, i);
        }
        for (int i = 0; i < COUNT; ++i) {
            assert sparseArray.containsKey(i);
        }
        System.out.println("SparseArray: " + ((System.currentTimeMillis() - start2) / 1000.0));

        long start1 = System.currentTimeMillis();
        HashMap<Integer, Integer> hashMap = new HashMap<>(16);
        for (int i = 0; i < COUNT; ++i) {
            hashMap.put(i, i);
        }
        for (int i = 0; i < COUNT; ++i) {
            assert hashMap.containsKey(i);
        }
        System.out.println("HashMap: " + ((System.currentTimeMillis() - start1) / 1000.0));
    }

    private static void testHashKeyPacking(int key) {
        long hk = calcHashKey(key);

        int hash = smear(key);
        if (hash == 0) {
            hash = 1;
        }

        assert key == (int)hk : "error extracting key";
        assert hash == (int)(hk >> 32) : "error extracting hash";
    }
}

