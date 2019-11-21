package com.github.netty.core;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Ordered
 * @author wangzihao
 *  2019/8/31/022
 */
public interface Ordered {
    /**
     * Priority order
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    int getOrder();

    /**
     * Avoid TreeSet overwrites data
     * return -1 or 1. not return 0
     */
    Comparator<Ordered> COMPARATOR = (c1, c2) ->
            c1.getOrder() < c2.getOrder() ? -1 : 1;


    /**
     * test comparator
     * @param args args
     */
    public static void main(String[] args) {
        /** no overwrites by {{@link COMPARATOR}} */
        TreeSet<Ordered> set1 = new TreeSet<>(COMPARATOR);
        set1.add(() -> 1);
        set1.add(() -> 1);
        System.out.println("no overwrites. set1 = " + set1.size());

        /** overwrites by jdk method {{@link Comparator#comparing(Function)}} */
        TreeSet<Ordered> set2 = new TreeSet<>(Comparator.comparing(Ordered::getOrder));
        set2.add(() -> 1);
        set2.add(() -> 1);
        System.out.println("overwrites. set2 = " + set2.size());
    }
}
