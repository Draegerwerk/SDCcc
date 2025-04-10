/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.util.junit.util;

import java.lang.reflect.Method;

/**
 * Utilities related to extracting information from classes.
 */
public class ClassUtilImpl implements ClassUtil {
    @Override
    public Method getMethod(final String className, final String methodName)
            throws ClassNotFoundException, NoSuchMethodException {
        final Class<?> clazz = Class.forName(className);
        return clazz.getDeclaredMethod(methodName);
    }
}
