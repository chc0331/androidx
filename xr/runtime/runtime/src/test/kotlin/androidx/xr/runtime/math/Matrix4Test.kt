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

package androidx.xr.runtime.math

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Matrix4Test {
    @Test
    fun constructorEquals_expectedToString_returnsTrue() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )

        assertThat(underTest.toString())
            .isEqualTo(
                "\n[ " +
                    1.0 +
                    "\t" +
                    5.0 +
                    "\t" +
                    9.0 +
                    "\t" +
                    13.0 +
                    "\n  " +
                    2.0 +
                    "\t" +
                    6.0 +
                    "\t" +
                    10.0 +
                    "\t" +
                    14.0 +
                    "\n  " +
                    3.0 +
                    "\t" +
                    7.0 +
                    "\t" +
                    11.0 +
                    "\t" +
                    15.0 +
                    "\n  " +
                    4.0 +
                    "\t" +
                    8.0 +
                    "\t" +
                    12.0 +
                    "\t" +
                    16.0 +
                    " ]"
            )
    }

    @Test
    fun constructor_fromMatrix4_returnsSameValues() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 = underTest

        assertThat(underTest).isEqualTo(underTest2)
    }

    @Test
    fun equals_sameValues_returnsTrue() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )

        assertThat(underTest).isEqualTo(underTest2)
    }

    @Test
    fun equals_differentValues_returnsFalse() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 =
            Matrix4(
                floatArrayOf(
                    9f,
                    10f,
                    11f,
                    12f,
                    13f,
                    14f,
                    15f,
                    16f,
                    17f,
                    18f,
                    19f,
                    20f,
                    21f,
                    22f,
                    23f,
                    24f
                )
            )

        assertThat(underTest).isNotEqualTo(underTest2)
    }

    @Test
    fun equals_differentObjects_returnsFalse() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 = Vector3()

        assertThat(underTest).isNotEqualTo(underTest2)
    }

    @Test
    fun hashCodeEquals_sameValues_returnsTrue() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )

        assertThat(underTest.hashCode()).isEqualTo(underTest2.hashCode())
    }

    @Test
    fun hashCodeEquals_differentValues_returnsFalse() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 =
            Matrix4(
                floatArrayOf(
                    9f,
                    10f,
                    11f,
                    12f,
                    13f,
                    14f,
                    15f,
                    16f,
                    17f,
                    18f,
                    19f,
                    20f,
                    21f,
                    22f,
                    23f,
                    24f
                )
            )

        assertThat(underTest.hashCode()).isNotEqualTo(underTest2.hashCode())
    }

    @Test
    fun hashCodeEquals_differentObjects_returnsFalse() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 = Vector3()

        assertThat(underTest.hashCode()).isNotEqualTo(underTest2.hashCode())
    }

    @Test
    fun inverse_returnsInverseMatrix1() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val expected =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTestInverse = underTest.inverse

        assertThat(underTestInverse.data).isEqualTo(expected.data)
    }

    @Test
    fun inverse_returnsInverseMatrix2() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 5f, -4f, 3f, 1f))
        val expected =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, -5f, 4f, -3f, 1f))
        val underTestInverse = underTest.inverse

        assertThat(underTestInverse.data).isEqualTo(expected.data)
    }

    @Test
    fun inverse_returnsInverseMatrix3() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 4f, 0f, 0f, 0f, 0f, 1f))
        val expected =
            Matrix4(
                floatArrayOf(1f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f, 0f, 0f, 0.25f, 0f, 0f, 0f, 0f, 1f)
            )
        val underTestInverse = underTest.inverse

        assertThat(underTestInverse.data).isEqualTo(expected.data)
    }

    @Test
    fun transpose_returnsTransposeMatrix1() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val expected =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTestTranspose = underTest.transpose

        assertThat(underTestTranspose.data).isEqualTo(expected.data)
    }

    @Test
    fun transpose_returnsTransposeMatrix2() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 5f, 9f, 13f, 2f, 6f, 10f, 14f, 3f, 7f, 11f, 15f, 4f, 8f, 12f, 16f)
            )
        val expected =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTestTranspose = underTest.transpose

        assertThat(underTestTranspose.data).isEqualTo(expected.data)
    }

    @Test
    fun translation_returnsTranslationVector1() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTestTranslation = underTest.translation

        assertThat(underTestTranslation).isEqualTo(Vector3(0f, 0f, 0f))
    }

    @Test
    fun translation_returnsTranslationVector2() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 5f, -4f, 3f, 1f))
        val underTestTranslation = underTest.translation

        assertThat(underTestTranslation).isEqualTo(Vector3(5f, -4f, 3f))
    }

    @Test
    fun translation_returnsTranslationVector3() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f, 1f))
        val underTestTranslation = underTest.translation

        assertThat(underTestTranslation).isEqualTo(Vector3(0f, 2f, 0f))
    }

    @Test
    fun scale_returnsScaleVector1() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTestScale = underTest.scale

        assertThat(underTestScale).isEqualTo(Vector3(1f, 1f, 1f))
    }

    @Test
    fun scale_returnsScaleVector2() {
        val underTest =
            Matrix4(floatArrayOf(5f, 0f, 0f, 0f, 0f, -4f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f))
        val underTestScale = underTest.scale

        assertThat(underTestScale).isEqualTo(Vector3(5f, -4f, 3f))
    }

    @Test
    fun scale_returnsScaleVector3() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 4f, 0f, 0f, 0f, 0f, 1f))
        val underTestScale = underTest.scale

        assertThat(underTestScale).isEqualTo(Vector3(1f, 2f, 4f))
    }

    @Test
    fun scale_returnsScaleVector4() {
        val underTest =
            Matrix4(
                floatArrayOf(
                    0.707f,
                    0.707f,
                    0f,
                    0f,
                    -0.707f,
                    0.707f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    2f,
                    5f,
                    3f,
                    1f,
                    1f,
                )
            )
        val underTestScale = underTest.scale

        assertThat(underTestScale.x).isWithin(1e-3f).of(1f)
        assertThat(underTestScale.y).isWithin(1e-3f).of(1f)
        assertThat(underTestScale.z).isWithin(1e-3f).of(1f)
    }

    @Test
    fun scale_returnsScaleVector5() {
        val underTest =
            Matrix4(
                floatArrayOf(
                    0.707f,
                    -0.5f,
                    0.5f,
                    0f,
                    0.612f,
                    0.707f,
                    0.354f,
                    0f,
                    -0.354f,
                    0.5f,
                    -0.793f,
                    0f,
                    2.5f,
                    1.8f,
                    3.1f,
                    1f,
                )
            )

        val underTestScale = underTest.scale

        assertThat(underTestScale.x).isWithin(1e-3f).of(1.0f)
        assertThat(underTestScale.y).isWithin(1e-3f).of(1.0f)
        assertThat(underTestScale.z).isWithin(1e-3f).of(-1.002f)
    }

    @Test
    fun rotation_returnsRotationQuaternion1() {
        val underTest =
            Matrix4(floatArrayOf(0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTestRotation = underTest.rotation

        assertThat(underTestRotation.x).isWithin(1e-5f).of(0f)
        assertThat(underTestRotation.y).isWithin(1e-5f).of(0f)
        assertThat(underTestRotation.z).isWithin(1e-5f).of(0.70711f)
        assertThat(underTestRotation.w).isWithin(1e-5f).of(0.70711f)
    }

    @Test
    fun rotation_returnsRotationQuaternion2() {
        val underTest =
            Matrix4(floatArrayOf(5f, 1f, 3f, 0f, -1f, 6f, 0f, 0f, 0f, 0f, 1f, 3f, 1f, 0f, 0f, 1f))
        val underTestRotation = underTest.rotation

        assertThat(underTestRotation.x).isWithin(1e-5f).of(0f)
        assertThat(underTestRotation.y).isWithin(1e-5f).of(-0.22237f)
        assertThat(underTestRotation.z).isWithin(1e-5f).of(0.14825f)
        assertThat(underTestRotation.w).isWithin(1e-5f).of(0.96362f)
    }

    @Test
    fun rotation_returnsRotationQuaternion3() {
        val underTest =
            Matrix4(floatArrayOf(5f, 1f, 3f, 2f, -1f, 6f, 4f, 0f, 2f, 0f, 1f, 3f, 1f, 2f, 3f, 4f))
        val underTestRotation = underTest.rotation

        assertThat(underTestRotation.x).isWithin(1e-5f).of(0.29019f)
        assertThat(underTestRotation.y).isWithin(1e-5f).of(-0.07254f)
        assertThat(underTestRotation.z).isWithin(1e-5f).of(0.14509f)
        assertThat(underTestRotation.w).isWithin(1e-5f).of(0.94312f)
    }

    @Test
    fun pose_returnsTranslationRotationPose1() {
        val underTest =
            Matrix4(floatArrayOf(0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTestPose = underTest.pose

        assertThat(underTestPose.translation.x).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.translation.y).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.translation.z).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.rotation.x).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.rotation.y).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.rotation.z).isWithin(1e-5f).of(0.70711f)
        assertThat(underTestPose.rotation.w).isWithin(1e-5f).of(0.70711f)
    }

    @Test
    fun pose_returnsTranslationRotationPose2() {
        val underTest =
            Matrix4(floatArrayOf(5f, 1f, 3f, 0f, -1f, 6f, 0f, 0f, 0f, 0f, 1f, 3f, 1f, 0f, 0f, 1f))
        val underTestPose = underTest.pose

        assertThat(underTestPose.translation.x).isWithin(1e-5f).of(1f)
        assertThat(underTestPose.translation.y).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.translation.z).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.rotation.x).isWithin(1e-5f).of(0f)
        assertThat(underTestPose.rotation.y).isWithin(1e-5f).of(-0.22237f)
        assertThat(underTestPose.rotation.z).isWithin(1e-5f).of(0.14825f)
        assertThat(underTestPose.rotation.w).isWithin(1e-5f).of(0.96362f)
    }

    @Test
    fun pose_returnsTranslationRotationPose3() {
        val underTest =
            Matrix4(floatArrayOf(5f, 1f, 3f, 2f, -1f, 6f, 4f, 0f, 2f, 0f, 1f, 3f, 1f, 2f, 3f, 4f))
        val underTestPose = underTest.pose

        assertThat(underTestPose.translation.x).isWithin(1e-5f).of(1f)
        assertThat(underTestPose.translation.y).isWithin(1e-5f).of(2f)
        assertThat(underTestPose.translation.z).isWithin(1e-5f).of(3f)
        assertThat(underTestPose.rotation.x).isWithin(1e-5f).of(0.29019f)
        assertThat(underTestPose.rotation.y).isWithin(1e-5f).of(-0.07255f)
        assertThat(underTestPose.rotation.z).isWithin(1e-5f).of(0.14509f)
        assertThat(underTestPose.rotation.w).isWithin(1e-5f).of(0.94312f)
    }

    @Test
    fun multiply_returnsMultipliedMatrix1() {
        val underTest =
            Matrix4(floatArrayOf(2f, 0f, 5f, 0f, 3f, 4f, 5f, 0f, 4f, 6f, 1f, 4f, 0f, 5f, 5f, 0f))
        val underTest2 =
            Matrix4(floatArrayOf(1f, 0f, 7f, 5f, 0f, 3f, 2f, 2f, 6f, 5f, 4f, 0f, 2f, 0f, 4f, 0f))

        assertThat(underTest * underTest2)
            .isEqualTo(
                Matrix4(
                    floatArrayOf(
                        30f,
                        67f,
                        37f,
                        28f,
                        17f,
                        34f,
                        27f,
                        8f,
                        43f,
                        44f,
                        59f,
                        16f,
                        20f,
                        24f,
                        14f,
                        16f,
                    )
                )
            )
    }

    @Test
    fun multiply_returnsMultipliedMatrix2() {
        val underTest =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        val underTest2 =
            Matrix4(
                floatArrayOf(1f, 5f, 9f, 13f, 2f, 6f, 10f, 14f, 3f, 7f, 11f, 15f, 4f, 8f, 12f, 16f)
            )

        assertThat(underTest * underTest2)
            .isEqualTo(
                Matrix4(
                    floatArrayOf(
                        1f,
                        5f,
                        9f,
                        13f,
                        2f,
                        6f,
                        10f,
                        14f,
                        3f,
                        7f,
                        11f,
                        15f,
                        4f,
                        8f,
                        12f,
                        16f
                    )
                )
            )
    }

    @Test
    fun multiply_returnsMultipliedMatrix3() {
        val underTest =
            Matrix4(floatArrayOf(1f, 3f, 1f, 3f, 2f, 3f, 2f, 3f, 3f, 3f, 3f, 3f, 4f, 3f, 4f, 3f))
        val underTest2 =
            Matrix4(floatArrayOf(1f, 2f, 1f, 2f, 2f, 2f, 2f, 2f, 3f, 2f, 3f, 2f, 4f, 2f, 4f, 2f))

        assertThat(underTest * underTest2)
            .isEqualTo(
                Matrix4(
                    floatArrayOf(
                        16f,
                        18f,
                        16f,
                        18f,
                        20f,
                        24f,
                        20f,
                        24f,
                        24f,
                        30f,
                        24f,
                        30f,
                        28f,
                        36f,
                        28f,
                        36f,
                    )
                )
            )
    }

    @Test
    fun copy_returnsCopyOfMatrix() {
        val underTest =
            Matrix4(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
        val underTest2 = underTest.copy()

        assertThat(underTest2).isEqualTo(underTest)
    }

    @Test
    fun isTrs_returnsTrueIfTransformationMatrixIsValid() {
        assertThat(
                Matrix4(
                        floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
                    )
                    .isTrs
            )
            .isTrue()
    }

    @Test
    fun isTrs_returnsFalseIfTransformationMatrixIsNotValid() {
        assertThat(
                Matrix4(
                        floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f)
                    )
                    .isTrs
            )
            .isFalse()
    }

    @Test
    fun fromTrs_returnsNewTransformationMatrix1() {
        val underTest =
            Matrix4.fromTrs(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f), Vector3(1f, 1f, 1f))
        val expected =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromTrs_returnsNewTransformationMatrix2() {
        val underTest =
            Matrix4.fromTrs(
                Vector3(0f, 0f, 0f),
                Quaternion(0f, 0f, 0.70710678f, 0.70710678f),
                Vector3(1f, 1f, 1f),
            )
        val expected =
            Matrix4(floatArrayOf(0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromTrs_returnsNewTransformationMatrix3() {
        val underTest =
            Matrix4.fromTrs(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f), Vector3(2f, 0.5f, 3f))
        val expected =
            Matrix4(floatArrayOf(2f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f))

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromTranslation_returnsNewTranslationMatrix1() {
        val underTest = Matrix4.fromTranslation(Vector3(2f, 3f, 4f))

        assertThat(underTest.data)
            .isEqualTo(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 2f, 3f, 4f, 1f))
    }

    @Test
    fun fromTranslation_returnsNewTranslationMatrix2() {
        val underTest = Matrix4.fromTranslation(Vector3(-2f, -3f, -4f))

        assertThat(underTest.data)
            .isEqualTo(
                floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, -2f, -3f, -4f, 1f)
            )
    }

    @Test
    fun fromScale_returnsNewScaleMatrix1() {
        val underTest = Matrix4.fromScale(Vector3(2f, 3f, 4f))

        assertThat(underTest.data)
            .isEqualTo(floatArrayOf(2f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 4f, 0f, 0f, 0f, 0f, 1f))
    }

    @Test
    fun fromScale_returnsNewScaleMatrix2() {
        val underTest = Matrix4.fromScale(Vector3(-2f, -3f, -4f))

        assertThat(underTest.data)
            .isEqualTo(
                floatArrayOf(-2f, 0f, 0f, 0f, 0f, -3f, 0f, 0f, 0f, 0f, -4f, 0f, 0f, 0f, 0f, 1f)
            )
    }

    @Test
    fun fromScaleFloat_returnsNewScaleMatrix1() {
        val underTest = Matrix4.fromScale(2f)

        assertThat(underTest.data)
            .isEqualTo(floatArrayOf(2f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 1f))
    }

    @Test
    fun fromScaleFloat_returnsNewScaleMatrix2() {
        val underTest = Matrix4.fromScale(-2f)

        assertThat(underTest.data)
            .isEqualTo(
                floatArrayOf(-2f, 0f, 0f, 0f, 0f, -2f, 0f, 0f, 0f, 0f, -2f, 0f, 0f, 0f, 0f, 1f)
            )
    }

    @Test
    fun fromQuaternion_returnsNewRotationMatrix1() {
        val underTest = Matrix4.fromQuaternion(Quaternion(0f, 0.7071f, 0f, 0.7071f))
        val expected =
            Matrix4(floatArrayOf(0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f))

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromQuaternion_returnsNewRotationMatrix2() {
        val underTest = Matrix4.fromQuaternion(Quaternion(0.7071f, 0f, 0.7071f, 0f))
        val expected =
            Matrix4(floatArrayOf(0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f))

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromQuaternion_returnsNewRotationMatrix3() {
        val underTest = Matrix4.fromQuaternion(Quaternion(0f, -0.38f, 0.92f, 0f))
        val expected =
            Matrix4(
                floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    -0.70852f,
                    -0.70569f,
                    0f,
                    0f,
                    -0.70569f,
                    0.70852f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                )
            )

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromPose_returnsNewTransformationMatrix1() {
        val underTest =
            Matrix4.fromPose(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, -0.38f, 0.92f, 0f)))
        val expected =
            Matrix4(
                floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    -0.70852f,
                    -0.70569f,
                    0f,
                    0f,
                    -0.70569f,
                    0.70852f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                )
            )

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromPose_returnsNewTransformationMatrix2() {
        val underTest = Matrix4.fromPose(Pose(Vector3(2f, 3f, 4f), Quaternion(0f, 0f, 0f, 1f)))
        val expected =
            Matrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 2f, 3f, 4f, 1f))

        assertMatrix(underTest, expected)
    }

    @Test
    fun fromPose_returnsNewTransformationMatrix3() {
        val underTest =
            Matrix4.fromPose(Pose(Vector3(5f, 6f, 7f), Quaternion(0f, 0.7071f, 0f, 0.7071f)))
        val expected =
            Matrix4(floatArrayOf(0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 5f, 6f, 7f, 1f))

        assertMatrix(underTest, expected)
    }

    private fun assertMatrix(matrix: Matrix4, expected: Matrix4) {
        assertThat(matrix.data.size).isEqualTo(expected.data.size)
        for (i in matrix.data.indices) {
            assertThat(matrix.data[i]).isWithin(1e-5f).of(expected.data[i])
        }
    }

    @Test
    fun data_returnsFloatArrayOfMatrixComponents() {
        val underTest =
            Matrix4(
                    floatArrayOf(
                        1f,
                        2f,
                        3f,
                        4f,
                        5f,
                        6f,
                        7f,
                        8f,
                        9f,
                        10f,
                        11f,
                        12f,
                        13f,
                        14f,
                        15f,
                        16f
                    )
                )
                .data

        assertThat(underTest)
            .isEqualTo(
                floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
            )
    }
}
