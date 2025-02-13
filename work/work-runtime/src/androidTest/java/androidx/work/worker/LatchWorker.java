/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.work.worker;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.CountDownLatch;

public class LatchWorker extends Worker {

    public CountDownLatch mLatch = new CountDownLatch(1);
    public CountDownLatch mEntrySignal = new CountDownLatch(1);
    public Result returnResult = Result.success();

    public LatchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public @NonNull Result doWork() {
        try {
            mEntrySignal.countDown();
            mLatch.await();
        } catch (InterruptedException e) {
            return Result.failure();
        }
        return returnResult;
    }
}
