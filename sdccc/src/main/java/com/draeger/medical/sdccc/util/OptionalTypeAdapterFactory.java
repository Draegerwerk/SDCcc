package com.draeger.medical.sdccc.util;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * A Gson TypeAdapterFactory for Optional types.
 */
public class OptionalTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> typeToken) {

        if (!Optional.class.isAssignableFrom(typeToken.getRawType())) {
            return null;
        }

        final Type typeOfT = typeToken.getType();
        final Type actualType;

        if (typeOfT instanceof ParameterizedType pt) {
            actualType = pt.getActualTypeArguments()[0];
        } else {
            actualType = Object.class;
        }

        final TypeAdapter<?> delegate = gson.getAdapter(TypeToken.get(actualType));
        return (TypeAdapter<T>) new OptionalTypeAdapter<>(delegate);
    }
}
