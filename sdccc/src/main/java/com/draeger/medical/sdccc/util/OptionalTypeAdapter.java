/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2025 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Optional;

/**
 * A Gson TypeAdapter for Optional types.
 *
 * @param <T> the type of the value contained in the Optional
 */
public class OptionalTypeAdapter<T> extends TypeAdapter<Optional<T>> {
    private final TypeAdapter<T> delegate;

    /**
     * Constructs an OptionalTypeAdapter with the given delegate TypeAdapter.
     *
     * @param delegate the TypeAdapter used to serialize/deserialize the contained value
     */
    public OptionalTypeAdapter(final TypeAdapter<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(final JsonWriter out, final Optional<T> value) throws IOException {
        if (value == null || value.isEmpty()) {
            out.nullValue();
        } else {
            delegate.write(out, value.orElseThrow());
        }
    }

    @Override
    public Optional<T> read(final JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return Optional.empty();
        }
        return Optional.ofNullable(delegate.read(in));
    }
}
