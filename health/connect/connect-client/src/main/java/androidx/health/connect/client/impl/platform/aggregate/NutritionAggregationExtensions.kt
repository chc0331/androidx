/*
 * Copyright 2024 The Android Open Source Project
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

@file:RequiresApi(api = 34)

package androidx.health.connect.client.impl.platform.aggregate

import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.impl.platform.div
import androidx.health.connect.client.impl.platform.duration
import androidx.health.connect.client.impl.platform.minus
import androidx.health.connect.client.impl.platform.toInstantWithDefaultZoneFallback
import androidx.health.connect.client.impl.platform.useLocalTime
import androidx.health.connect.client.records.IntervalRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.math.max

internal suspend fun HealthConnectClient.aggregateNutritionTransFatTotal(
    aggregateRequest: AggregateRequest
): AggregationResult {
    val aggregator = TransFatTotalAggregator(aggregateRequest.timeRangeFilter)

    readRecordsFlow(
            ReadRecordsRequest(
                NutritionRecord::class,
                aggregateRequest.timeRangeFilter.withBufferedStart(),
                aggregateRequest.dataOriginFilter
            )
        )
        .collect { records ->
            records
                .filter { it.overlaps(aggregateRequest.timeRangeFilter) }
                .forEach { aggregator += it }
        }

    return aggregator.getResult()
}

internal fun IntervalRecord.sliceFactor(timeRangeFilter: TimeRangeFilter): Double {
    val startTime: Instant
    val endTime: Instant

    if (timeRangeFilter.useLocalTime()) {
        val requestStartTime =
            timeRangeFilter.localStartTime?.toInstantWithDefaultZoneFallback(startZoneOffset)
        val requestEndTime =
            timeRangeFilter.localEndTime?.toInstantWithDefaultZoneFallback(endZoneOffset)
        startTime = maxOf(this.startTime, requestStartTime ?: this.startTime)
        endTime = minOf(this.endTime, requestEndTime ?: this.endTime)
    } else {
        startTime = maxOf(this.startTime, timeRangeFilter.startTime ?: this.startTime)
        endTime = minOf(this.endTime, timeRangeFilter.endTime ?: this.endTime)
    }

    return max(0.0, (endTime - startTime) / duration)
}

private class TransFatTotalAggregator(val timeRangeFilter: TimeRangeFilter) :
    SingeResultAggregator<NutritionRecord>() {
    var total = 0.0

    override val dataOrigins = mutableSetOf<DataOrigin>()
    override val doubleValues: Map<String, Double>
        get() = mapOf(NutritionRecord.TRANS_FAT_TOTAL.metricKey to total)

    override operator fun plusAssign(value: NutritionRecord) {
        if (value.transFat != null && value.sliceFactor(timeRangeFilter) > 0) {
            total += value.transFat.inGrams * value.sliceFactor(timeRangeFilter)
            dataOrigins += value.metadata.dataOrigin
        }
    }
}
