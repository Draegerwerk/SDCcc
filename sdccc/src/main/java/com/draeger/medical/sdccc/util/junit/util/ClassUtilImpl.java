/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
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
