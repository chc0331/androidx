/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.core.impl.utils;

import androidx.core.util.Preconditions;
import androidx.core.util.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of an {@link Optional} not containing a reference.
 *
 * <p>Copied and adapted from Guava.
 */
final class Absent<T> extends Optional<T> {
    static final Absent<Object> sInstance = new Absent<>();

    @SuppressWarnings("unchecked") // implementation is "fully variant"
    static <T> Optional<T> withType() {
        return (Optional<T>) sInstance;
    }

    private Absent() {
    }

    @Override
    public boolean isPresent() {
        return false;
    }

    @Override
    public @NonNull T get() {
        throw new IllegalStateException("Optional.get() cannot be called on an absent value");
    }

    @Override
    public @NonNull T or(@NonNull T defaultValue) {
        return Preconditions.checkNotNull(defaultValue,
                "use Optional.orNull() instead of Optional.or(null)");
    }

    @SuppressWarnings("unchecked") // safe covariant cast
    @Override
    public @NonNull Optional<T> or(@NonNull Optional<? extends T> secondChoice) {
        return (Optional<T>) Preconditions.checkNotNull(secondChoice);
    }

    @Override
    public @NonNull T or(@NonNull Supplier<? extends T> supplier) {
        return Preconditions.checkNotNull(
                supplier.get(), "use Optional.orNull() instead of a Supplier that returns null");
    }

    @Override
    public @Nullable T orNull() {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object == this;
    }

    @Override
    public int hashCode() {
        return 0x79a31aac;
    }

    @Override
    public @NonNull String toString() {
        return "Optional.absent()";
    }

    private Object readResolve() {
        return sInstance;
    }

    private static final long serialVersionUID = 0;
}
