/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.testing.impl.fakes;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.ExtendableBuilder;
import androidx.camera.core.impl.Config;
import androidx.camera.core.impl.MutableConfig;
import androidx.camera.core.impl.MutableOptionsBundle;
import androidx.camera.core.impl.OptionsBundle;
import androidx.camera.core.impl.ReadableConfig;

import org.jspecify.annotations.NonNull;

/**
 * Wrapper for an empty Config
 *
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public final class FakeConfig implements ReadableConfig {

    private final Config mConfig;

    FakeConfig(Config config) {
        mConfig = config;
    }

    @Override
    public @NonNull Config getConfig() {
        return mConfig;
    }

    /** Builder for an empty Config */
    public static final class Builder implements ExtendableBuilder<FakeConfig> {

        private final MutableOptionsBundle mOptionsBundle;

        public Builder() {
            mOptionsBundle = MutableOptionsBundle.create();
        }

        @Override
        public @NonNull MutableConfig getMutableConfig() {
            return mOptionsBundle;
        }

        /**
         * Builds an immutable {@link FakeConfig} from the current state.
         *
         * @return A {@link FakeConfig} populated with the current state.
         */
        @Override
        public @NonNull FakeConfig build() {
            return new FakeConfig(OptionsBundle.from(mOptionsBundle));
        }
    }
}
