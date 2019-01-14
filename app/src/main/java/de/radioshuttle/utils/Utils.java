/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Utils {
    
    public static ExecutorService executor = Executors.newCachedThreadPool();
}
