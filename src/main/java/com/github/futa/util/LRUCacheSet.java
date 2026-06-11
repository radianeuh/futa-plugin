package com.github.futa.util;

import java.util.*;

/**
 * 基于 LRU（最近最少使用）策略的缓存集合。
 * 继承 AbstractSet，底层使用 LinkedHashMap 实现。
 *
 * 特性：
 * - 满足 Set 接口语义（不重复元素）
 * - 超出容量时自动淘汰最久未访问的元素
 * - contains() / add() / remove() 均为 O(1)
 *
 * @param <E> 元素类型
 */
public class LRUCacheSet<E> extends AbstractSet<E> {

    private final int capacity;

    // LinkedHashMap accessOrder=true：每次 get/put 都会把节点移到链尾，
    // 链头即为最久未访问的元素，removeEldestEntry 负责淘汰。
    private final LinkedHashMap<E, Boolean> map;

    /**
     * 构造一个指定容量的 LRU 缓存集合。
     *
     * @param capacity 最大容量（必须 > 0）
     */
    public LRUCacheSet(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必须大于 0，实际值：" + capacity);
        }
        this.capacity = capacity;
        // initialCapacity 略大于 capacity 以避免扩容，loadFactor=0.75，accessOrder=true
        this.map = new LinkedHashMap<>(capacity + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<E, Boolean> eldest) {
                // 当元素数量超出容量时，自动移除最久未访问的元素（链头）
                return size() > capacity;
            }
        };
    }

    // -------------------------------------------------------------------------
    // AbstractSet / AbstractCollection 必须实现的方法
    // -------------------------------------------------------------------------

    /**
     * 返回集合中的元素数量。
     */
    @Override
    public int size() {
        return map.size();
    }

    /**
     * 返回迭代器（按访问顺序从旧到新）。
     */
    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    // -------------------------------------------------------------------------
    // 核心操作
    // -------------------------------------------------------------------------

    /**
     * 将元素加入缓存。
     * 若元素已存在则刷新其访问时间（移至最新位置）；
     * 若缓存已满则先淘汰最久未访问的元素，再插入。
     *
     * @param element 要添加的元素
     * @return 若集合因此调用发生了变化则返回 true
     */
    @Override
    public boolean add(E element) {
        if (map.containsKey(element)) {
            // 已存在：通过 get 刷新访问顺序
            map.get(element);
            return false;
        }
        map.put(element, Boolean.TRUE);
        return true;
    }

    /**
     * 判断元素是否存在于缓存中，并刷新其访问时间。
     *
     * @param o 要查找的对象
     * @return 存在返回 true，否则 false
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        // 使用 get 而非 containsKey，以触发 accessOrder 更新
        return map.get(o) != null;
    }

    /**
     * 从缓存中移除指定元素。
     *
     * @param o 要移除的对象
     * @return 若元素存在并被成功移除则返回 true
     */
    @Override
    public boolean remove(Object o) {
        return map.remove(o) != null;
    }

    /**
     * 清空缓存。
     */
    @Override
    public void clear() {
        map.clear();
    }

    /**
     * 返回该缓存集合的最大容量。
     */
    public int capacity() {
        return capacity;
    }

    /**
     * 窥视（peek）最久未访问的元素，不改变访问顺序。
     * 若缓存为空则返回 null。
     */
    public E peekLeastRecentlyUsed() {
        if (map.isEmpty()) return null;
        return map.keySet().iterator().next();
    }

    /**
     * 窥视最近访问的元素，不改变访问顺序。
     * 若缓存为空则返回 null。
     */
    public E peekMostRecentlyUsed() {
        if (map.isEmpty()) return null;
        E last = null;
        for (E key : map.keySet()) {
            last = key;
        }
        return last;
    }

    @Override
    public String toString() {
        return "LRUCacheSet{capacity=" + capacity + ", elements=" + map.keySet() + "}";
    }

    // -------------------------------------------------------------------------
    // 简单演示
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        LRUCacheSet<Integer> cache = new LRUCacheSet<>(3);

        cache.add(1);
        cache.add(2);
        cache.add(3);
        System.out.println("初始状态: " + cache);
        // => LRUCacheSet{capacity=3, elements=[1, 2, 3]}

        // 访问 1，使其成为最近使用
        cache.contains(1);
        System.out.println("访问 1 后: " + cache);

        // 插入 4，容量已满，淘汰最久未访问的 2
        cache.add(4);
        System.out.println("插入 4 后（淘汰 2）: " + cache);
        // => LRUCacheSet{capacity=3, elements=[3, 1, 4]}

        System.out.println("最久未访问: " + cache.peekLeastRecentlyUsed()); // 3
        System.out.println("最近访问:   " + cache.peekMostRecentlyUsed());  // 4

        // 重复添加已有元素不改变 size，但刷新访问顺序
        boolean changed = cache.add(3);
        System.out.println("重复添加 3，集合是否变化: " + changed);           // false
        System.out.println("访问 3 后最近访问: " + cache.peekMostRecentlyUsed()); // 3
    }
}
