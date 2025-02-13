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

package androidx.xr.scenecore.impl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import androidx.xr.extensions.node.InputEvent;
import androidx.xr.extensions.node.Node;
import androidx.xr.extensions.node.Vec3;
import androidx.xr.scenecore.JxrPlatformAdapter;
import androidx.xr.scenecore.JxrPlatformAdapter.Entity;
import androidx.xr.scenecore.JxrPlatformAdapter.InputEventListener;
import androidx.xr.scenecore.JxrPlatformAdapter.PointerCaptureComponent;
import androidx.xr.scenecore.JxrPlatformAdapter.PointerCaptureComponent.StateListener;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;
import androidx.xr.scenecore.testing.FakeXrExtensions;
import androidx.xr.scenecore.testing.FakeXrExtensions.FakeInputEvent;
import androidx.xr.scenecore.testing.FakeXrExtensions.FakeNode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PointerCaptureComponentImplTest {

    // Static private implementation of fakes so that the last received state can be grabbed.
    private static class FakeStateListener implements StateListener {
        public int lastState = -1;

        @Override
        public void onStateChanged(int newState) {
            lastState = newState;
        }
    }

    private static class FakeInputEventListener implements InputEventListener {
        public JxrPlatformAdapter.InputEvent lastEvent = null;

        @Override
        public void onInputEvent(JxrPlatformAdapter.InputEvent event) {
            lastEvent = event;
        }
    }

    private final FakeStateListener stateListener = new FakeStateListener();

    private final FakeInputEventListener inputListener = new FakeInputEventListener();

    private final FakeXrExtensions fakeExtensions = new FakeXrExtensions();
    private final FakeScheduledExecutorService fakeScheduler = new FakeScheduledExecutorService();
    private final FakeNode fakeNode = (FakeNode) fakeExtensions.createNode();

    private final Entity entity =
            new AndroidXrEntity(fakeNode, fakeExtensions, new EntityManager(), fakeScheduler) {};

    @Test
    public void onAttach_enablesPointerCapture() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);

        assertThat(component.onAttach(entity)).isTrue();

        assertThat(fakeNode.getPointerCaptureStateCallback()).isNotNull();
    }

    @Test
    public void onAttach_setsUpInputEventPropagation() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        FakeInputEvent fakeInput = new FakeInputEvent();
        fakeInput.setDispatchFlags(InputEvent.DISPATCH_FLAG_CAPTURED_POINTER);
        fakeInput.setOrigin(new Vec3(0, 0, 0));
        fakeInput.setDirection(new Vec3(1, 1, 1));
        fakeNode.sendInputEvent(fakeInput);
        fakeScheduler.runAll();

        assertThat(inputListener.lastEvent).isNotNull();
    }

    // This should really be a test on AndroidXrEntity, but that does not have tests so it is here
    // for
    // the meantime.
    @Test
    public void onAttach_onlyPropagatesCapturedEvents() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        FakeInputEvent fakeCapturedInput = new FakeInputEvent();
        fakeCapturedInput.setDispatchFlags(InputEvent.DISPATCH_FLAG_CAPTURED_POINTER);
        fakeCapturedInput.setTimestamp(100);
        fakeCapturedInput.setOrigin(new Vec3(0, 0, 0));
        fakeCapturedInput.setDirection(new Vec3(1, 1, 1));

        FakeInputEvent fakeInput = new FakeInputEvent();
        fakeInput.setTimestamp(200);
        fakeInput.setOrigin(new Vec3(0, 0, 0));
        fakeInput.setDirection(new Vec3(1, 1, 1));

        fakeNode.sendInputEvent(fakeCapturedInput);
        fakeNode.sendInputEvent(fakeInput);

        fakeScheduler.runAll();

        assertThat(inputListener.lastEvent).isNotNull();
        assertThat(inputListener.lastEvent.timestamp).isEqualTo(fakeCapturedInput.getTimestamp());
    }

    @Test
    public void onAttach_propagatesInputOnCorrectThread() {
        FakeScheduledExecutorService propagationExecutor = new FakeScheduledExecutorService();
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(propagationExecutor, stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        FakeInputEvent fakeCapturedInput = new FakeInputEvent();
        fakeCapturedInput.setDispatchFlags(InputEvent.DISPATCH_FLAG_CAPTURED_POINTER);
        fakeCapturedInput.setTimestamp(100);
        fakeCapturedInput.setOrigin(new Vec3(0, 0, 0));
        fakeCapturedInput.setDirection(new Vec3(1, 1, 1));

        fakeNode.sendInputEvent(fakeCapturedInput);

        assertThat(propagationExecutor.hasNext()).isFalse();
        // Run the scheduler associated with the Entity so that the component's executor has the
        // task
        // scheduled on it.
        fakeScheduler.runAll();

        assertThat(propagationExecutor.hasNext()).isTrue();
    }

    @Test
    public void onAttach_setsUpCorrectStatePropagation() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        fakeNode.getPointerCaptureStateCallback().accept(Node.POINTER_CAPTURE_STATE_PAUSED);
        assertThat(stateListener.lastState)
                .isEqualTo(PointerCaptureComponent.POINTER_CAPTURE_STATE_PAUSED);

        fakeNode.getPointerCaptureStateCallback().accept(Node.POINTER_CAPTURE_STATE_ACTIVE);
        assertThat(stateListener.lastState)
                .isEqualTo(PointerCaptureComponent.POINTER_CAPTURE_STATE_ACTIVE);

        fakeNode.getPointerCaptureStateCallback().accept(Node.POINTER_CAPTURE_STATE_STOPPED);
        assertThat(stateListener.lastState)
                .isEqualTo(PointerCaptureComponent.POINTER_CAPTURE_STATE_STOPPED);
    }

    @Test
    public void onAttach_failsIfAlreadyAttached() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();
        assertThat(component.onAttach(entity)).isFalse();
    }

    @Test
    public void onAttach_failesIfEntityAlreadyHasAnAttachedPointerCaptureComponent() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        PointerCaptureComponentImpl component2 =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component2.onAttach(entity)).isFalse();
    }

    @Test
    public void onDetach_stopsPointerCapture() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        component.onDetach(entity);

        assertThat(fakeNode.getPointerCaptureStateCallback()).isNull();
    }

    @Test
    public void onDetach_removesInputListener() {
        PointerCaptureComponentImpl component =
                new PointerCaptureComponentImpl(directExecutor(), stateListener, inputListener);
        assertThat(component.onAttach(entity)).isTrue();

        component.onDetach(entity);

        assertThat(fakeNode.getListener()).isNull();
    }
}
