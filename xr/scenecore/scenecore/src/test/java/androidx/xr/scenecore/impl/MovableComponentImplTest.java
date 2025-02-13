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

import static androidx.xr.runtime.testing.math.MathAssertions.assertPose;
import static androidx.xr.runtime.testing.math.MathAssertions.assertVector3;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.IBinder;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import androidx.xr.extensions.node.Mat4f;
import androidx.xr.extensions.node.Node;
import androidx.xr.extensions.node.Quatf;
import androidx.xr.extensions.node.ReformEvent;
import androidx.xr.extensions.node.ReformOptions;
import androidx.xr.extensions.node.Vec3;
import androidx.xr.runtime.math.Matrix4;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Quaternion;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.JxrPlatformAdapter;
import androidx.xr.scenecore.JxrPlatformAdapter.AnchorEntity;
import androidx.xr.scenecore.JxrPlatformAdapter.AnchorPlacement;
import androidx.xr.scenecore.JxrPlatformAdapter.Dimensions;
import androidx.xr.scenecore.JxrPlatformAdapter.Entity;
import androidx.xr.scenecore.JxrPlatformAdapter.MovableComponent;
import androidx.xr.scenecore.JxrPlatformAdapter.MoveEvent;
import androidx.xr.scenecore.JxrPlatformAdapter.MoveEventListener;
import androidx.xr.scenecore.JxrPlatformAdapter.PanelEntity;
import androidx.xr.scenecore.JxrPlatformAdapter.PixelDimensions;
import androidx.xr.scenecore.JxrPlatformAdapter.PlaneSemantic;
import androidx.xr.scenecore.JxrPlatformAdapter.PlaneType;
import androidx.xr.scenecore.impl.perception.Anchor;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Plane;
import androidx.xr.scenecore.impl.perception.Plane.PlaneData;
import androidx.xr.scenecore.impl.perception.Session;
import androidx.xr.scenecore.testing.FakeImpressApi;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;
import androidx.xr.scenecore.testing.FakeXrExtensions;
import androidx.xr.scenecore.testing.FakeXrExtensions.FakeNode;
import androidx.xr.scenecore.testing.FakeXrExtensions.FakeNodeTransform;
import androidx.xr.scenecore.testing.FakeXrExtensions.FakeReformEvent;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
public class MovableComponentImplTest {
    private final ActivityController<Activity> activityController =
            Robolectric.buildActivity(Activity.class);
    private final Activity activity = activityController.create().start().get();
    private final FakeScheduledExecutorService fakeExecutor = new FakeScheduledExecutorService();
    private final PerceptionLibrary perceptionLibrary = mock(PerceptionLibrary.class);
    private final FakeXrExtensions fakeExtensions = new FakeXrExtensions();
    private final FakeImpressApi fakeImpressApi = new FakeImpressApi();
    private final EntityManager entityManager = new EntityManager();

    private final SplitEngineSubspaceManager splitEngineSubspaceManager =
            Mockito.mock(SplitEngineSubspaceManager.class);
    private final ImpSplitEngineRenderer splitEngineRenderer =
            Mockito.mock(ImpSplitEngineRenderer.class);
    private JxrPlatformAdapter fakeRuntime;
    private ActivitySpaceImpl activitySpaceImpl;
    private Node activitySpaceNode;
    private final AndroidXrEntity activitySpaceRoot = Mockito.mock(AndroidXrEntity.class);
    private PerceptionSpaceActivityPoseImpl perceptionSpaceActivityPose;
    private final PanelShadowRenderer panelShadowRenderer = Mockito.mock(PanelShadowRenderer.class);

    @Rule public final Expect expect = Expect.create();

    @Before
    public void setUp() {
        when(perceptionLibrary.initSession(eq(activity), anyInt(), eq(fakeExecutor)))
                .thenReturn(immediateFuture(mock(Session.class)));
        fakeRuntime =
                JxrPlatformAdapterAxr.create(
                        activity,
                        fakeExecutor,
                        fakeExtensions,
                        fakeImpressApi,
                        entityManager,
                        perceptionLibrary,
                        splitEngineSubspaceManager,
                        splitEngineRenderer,
                        /* useSplitEngine= */ false);
        activitySpaceImpl = (ActivitySpaceImpl) fakeRuntime.getActivitySpace();
        activitySpaceNode = activitySpaceImpl.getNode();
        perceptionSpaceActivityPose =
                (PerceptionSpaceActivityPoseImpl) fakeRuntime.getPerceptionSpaceActivityPose();
    }

    private Entity createTestEntity() {
        return fakeRuntime.createEntity(new Pose(), "test", fakeRuntime.getActivitySpace());
    }

    private PanelEntity createTestPanelEntity() {
        Display display = activity.getSystemService(DisplayManager.class).getDisplays()[0];
        Context displayContext = activity.createDisplayContext(display);
        View view = new View(displayContext);
        view.setLayoutParams(new LayoutParams(640, 480));
        SurfaceControlViewHost surfaceControlViewHost =
                new SurfaceControlViewHost(
                        displayContext,
                        Objects.requireNonNull(displayContext.getDisplay()),
                        new Binder());
        surfaceControlViewHost.setView(view, 10, 10);
        Node node = fakeExtensions.createNode();

        PanelEntityImpl panelEntity =
                new PanelEntityImpl(
                        node,
                        fakeExtensions,
                        entityManager,
                        surfaceControlViewHost,
                        new PixelDimensions(10, 10),
                        fakeExecutor);
        panelEntity.setParent(activitySpaceImpl);
        return panelEntity;
    }

    private void setActivitySpacePose(Pose pose, float scale) {
        Matrix4 poseMatrix = Matrix4.fromPose(pose);
        Matrix4 scaleMatrix = Matrix4.fromScale(scale);
        Matrix4 scaledPoseMatrix = poseMatrix.times(scaleMatrix);
        Mat4f mat4f = new Mat4f(scaledPoseMatrix.getData());
        FakeNodeTransform nodeTransformEvent = new FakeNodeTransform(mat4f);

        ((FakeNode) activitySpaceNode).sendTransformEvent(nodeTransformEvent);
        fakeExecutor.runAll();
    }

    private ImmutableSet<JxrPlatformAdapter.AnchorPlacement> createAnyAnchorPlacement() {
        JxrPlatformAdapter.AnchorPlacement anchorPlacement =
                fakeRuntime.createAnchorPlacementForPlanes(
                        ImmutableSet.of(PlaneType.ANY), ImmutableSet.of(PlaneSemantic.ANY));
        return ImmutableSet.of(anchorPlacement);
    }

    @Test
    @Ignore
    public void addMovableComponent_addsReformOptionsToNode() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ false,
                        /* scaleInZ= */ false,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        assertThat(node.getReformOptions().getEnabledReform()).isEqualTo(ReformOptions.ALLOW_MOVE);
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_POSE_RELATIVE_TO_PARENT);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();
    }

    @Test
    @Ignore
    public void addMovableComponent_addsSystemMovableFlagToNode() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ false,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        assertThat(node.getReformOptions().getEnabledReform()).isEqualTo(ReformOptions.ALLOW_MOVE);
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(
                        ReformOptions.FLAG_ALLOW_SYSTEM_MOVEMENT
                                | ReformOptions.FLAG_POSE_RELATIVE_TO_PARENT);
    }

    @Test
    @Ignore
    public void addMovableComponent_addsScaleInZFlagToNode() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ false,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        assertThat(node.getReformOptions().getEnabledReform()).isEqualTo(ReformOptions.ALLOW_MOVE);
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(
                        ReformOptions.FLAG_SCALE_WITH_DISTANCE
                                | ReformOptions.FLAG_POSE_RELATIVE_TO_PARENT);
    }

    @Test
    @Ignore
    public void addMovableComponent_addsAllFlagsToNode() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        assertThat(node.getReformOptions().getEnabledReform()).isEqualTo(ReformOptions.ALLOW_MOVE);
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(
                        ReformOptions.FLAG_SCALE_WITH_DISTANCE
                                | ReformOptions.FLAG_ALLOW_SYSTEM_MOVEMENT
                                | ReformOptions.FLAG_POSE_RELATIVE_TO_PARENT);
    }

    @Test
    @Ignore
    public void setSystemMovableFlag_alsoUpdatesEntityPoseAndScale() {
        AndroidXrEntity entity = (AndroidXrEntity) createTestEntity();
        MovableComponentImpl movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(entity.addComponent(movableComponent)).isTrue();
        Pose expectedPose =
                new Pose(new Vector3(2f, 2f, 2f), new Quaternion(0.5f, 0.5f, 0.5f, 0.5f));
        Vector3 expectedScale = new Vector3(1.2f, 1.2f, 1.2f);

        entity.setPose(new Pose(new Vector3(1f, 1f, 1f), new Quaternion(0f, 0f, 0f, 1f)));
        entity.setScale(new Vector3(1f, 1f, 1f));

        FakeNode node = (FakeNode) entity.getNode();
        FakeReformEvent reformEvent = new FakeReformEvent();
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setProposedPosition(new Vec3(2f, 2f, 2f));
        reformEvent.setProposedOrientation(new Quatf(0.5f, 0.5f, 0.5f, 0.5f));
        reformEvent.setProposedScale(new Vec3(1.2f, 1.2f, 1.2f));
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();

        expect.that(entity.getPose()).isEqualTo(expectedPose);
        expect.that(entity.getScale()).isEqualTo(expectedScale);
    }

    @Test
    @Ignore
    public void systemMovableFlagNotSet_doesNotUpdateEntityPoseAndScale() {
        AndroidXrEntity entity = (AndroidXrEntity) createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ false,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(entity.addComponent(movableComponent)).isTrue();
        Pose expectedPose = new Pose(new Vector3(1f, 1f, 1f), new Quaternion(0f, 0f, 0f, 1f));
        Vector3 expectedScale = new Vector3(1f, 1f, 1f);
        entity.setPose(new Pose(new Vector3(1f, 1f, 1f), new Quaternion(0f, 0f, 0f, 1f)));
        entity.setScale(new Vector3(1f, 1f, 1f));

        FakeNode node = (FakeNode) entity.getNode();
        FakeReformEvent reformEvent = new FakeReformEvent();
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setProposedPosition(new Vec3(2f, 2f, 2f));
        reformEvent.setProposedOrientation(new Quatf(0.5f, 0.5f, 0.5f, 0.5f));
        reformEvent.setProposedScale(new Vec3(1.2f, 1.2f, 1.2f));
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();

        expect.that(entity.getPose()).isEqualTo(expectedPose);
        expect.that(entity.getScale()).isEqualTo(expectedScale);
    }

    @Test
    @Ignore
    public void setSizeOnMovableComponent_setsSizeOnNodeReformOptions() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        movableComponent.setSize(new Dimensions(2f, 2f, 2f));
        assertThat(node.getReformOptions().getCurrentSize().x).isEqualTo(2f);
        assertThat(node.getReformOptions().getCurrentSize().y).isEqualTo(2f);
        assertThat(node.getReformOptions().getCurrentSize().z).isEqualTo(2f);
    }

    @Test
    @Ignore
    public void scaleWithDistanceOnMovableComponent_defaultsToDefaultMode() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        // Default value for scaleWithDistanceMode is DEFAULT.
        assertThat(movableComponent.getScaleWithDistanceMode())
                .isEqualTo(MovableComponent.ScaleWithDistanceMode.DEFAULT);
        assertThat(entity.addComponent(movableComponent)).isTrue();
        assertThat(node.getReformOptions().getScaleWithDistanceMode())
                .isEqualTo(ReformOptions.SCALE_WITH_DISTANCE_MODE_DEFAULT);
    }

    @Test
    @Ignore
    public void setScaleWithDistanceOnMovableComponent_setsScaleWithDistanceOnNodeReformOptions() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        assertThat(entity.addComponent(movableComponent)).isTrue();

        movableComponent.setScaleWithDistanceMode(MovableComponent.ScaleWithDistanceMode.DMM);

        assertThat(movableComponent.getScaleWithDistanceMode())
                .isEqualTo(MovableComponent.ScaleWithDistanceMode.DMM);
        assertThat(node.getReformOptions().getScaleWithDistanceMode())
                .isEqualTo(ReformOptions.SCALE_WITH_DISTANCE_MODE_DMM);
    }

    @Test
    @Ignore
    public void setPropertiesOnMovableComponentAttachLater_setsPropertiesOnNodeReformOptions() {
        AndroidXrEntity entity = (AndroidXrEntity) createTestEntity();
        MovableComponentImpl movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        movableComponent.setSize(new Dimensions(2f, 2f, 2f));
        MoveEventListener mockMoveEventListener = mock(MoveEventListener.class);
        movableComponent.addMoveEventListener(directExecutor(), mockMoveEventListener);
        assertThat(movableComponent.reformEventConsumer).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) entity.getNode();

        assertThat(node.getReformOptions().getCurrentSize().x).isEqualTo(2f);
        assertThat(node.getReformOptions().getCurrentSize().y).isEqualTo(2f);
        assertThat(node.getReformOptions().getCurrentSize().z).isEqualTo(2f);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();
        assertThat(entity.reformEventConsumerMap).isNotEmpty();
    }

    @Test
    @Ignore
    public void addMoveEventListener_onlyInvokedOnMoveEvent() {
        AndroidXrEntity entity = (AndroidXrEntity) createTestEntity();
        Vector3 initialTranslation = new Vector3(1f, 2f, 3f);
        Vector3 initialScale = new Vector3(1.1f, 1.1f, 1.1f);
        entity.setPose(new Pose(initialTranslation, new Quaternion()));
        entity.setScale(initialScale);
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) entity.getNode();
        MoveEventListener mockMoveEventListener = mock(MoveEventListener.class);

        movableComponent.addMoveEventListener(directExecutor(), mockMoveEventListener);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();
        assertThat(entity.reformEventConsumerMap).isNotEmpty();

        FakeReformEvent reformEvent = new FakeReformEvent();
        reformEvent.setType(ReformEvent.REFORM_TYPE_RESIZE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        verify(mockMoveEventListener, never()).onMoveEvent(any());

        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        ArgumentCaptor<MoveEvent> moveEventCaptor = ArgumentCaptor.forClass(MoveEvent.class);
        verify(mockMoveEventListener).onMoveEvent(moveEventCaptor.capture());
        MoveEvent moveEvent = moveEventCaptor.getValue();
        assertThat(moveEvent.previousPose.getTranslation()).isEqualTo(initialTranslation);
        assertThat(moveEvent.previousScale).isEqualTo(initialScale);
    }

    @Test
    @Ignore
    public void addMoveEventListenerWithExecutor_invokesListenerOnGivenExecutor() {
        Entity entity = createTestEntity();
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        MoveEventListener mockMoveEventListener = mock(MoveEventListener.class);
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, mockMoveEventListener);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();
        verify(mockMoveEventListener).onMoveEvent(any());
    }

    @Test
    @Ignore
    public void addMoveEventListenerMultiple_invokesAllListeners() {
        Entity entity = createTestEntity();
        MovableComponentImpl movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        MoveEventListener mockMoveEventListener1 = mock(MoveEventListener.class);
        MoveEventListener mockMoveEventListener2 = mock(MoveEventListener.class);
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, mockMoveEventListener1);
        movableComponent.addMoveEventListener(executorService, mockMoveEventListener2);
        FakeReformEvent reformEvent = new FakeReformEvent();
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        verify(mockMoveEventListener1).onMoveEvent(any());
        verify(mockMoveEventListener2).onMoveEvent(any());
    }

    @Test
    @Ignore
    public void removeMoveEventListenerMultiple_removesGivenListener() {
        Entity entity = createTestEntity();
        MovableComponentImpl movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        MoveEventListener mockMoveEventListener1 = mock(MoveEventListener.class);
        MoveEventListener mockMoveEventListener2 = mock(MoveEventListener.class);
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, mockMoveEventListener1);
        movableComponent.addMoveEventListener(executorService, mockMoveEventListener2);
        FakeReformEvent reformEvent = new FakeReformEvent();
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        fakeExecutor.runAll();
        executorService.runAll();

        // Verify both listeners are invoked.
        verify(mockMoveEventListener1).onMoveEvent(any());
        verify(mockMoveEventListener2).onMoveEvent(any());

        movableComponent.removeMoveEventListener(mockMoveEventListener1);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The first listener, which we removed, should not be invoked again.
        verify(mockMoveEventListener1).onMoveEvent(any());
        verify(mockMoveEventListener2, times(2)).onMoveEvent(any());
    }

    @Test
    @Ignore
    public void removeMovableComponent_clearsMoveReformOptionsAndMoveEventListeners() {
        Entity entity = createTestEntity();
        MovableComponentImpl movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        MoveEventListener mockMoveEventListener = mock(MoveEventListener.class);

        movableComponent.addMoveEventListener(directExecutor(), mockMoveEventListener);
        assertThat(movableComponent.reformEventConsumer).isNotNull();
        assertThat(((AndroidXrEntity) entity).reformEventConsumerMap).isNotEmpty();

        entity.removeComponent(movableComponent);
        assertThat(node.getReformOptions().getEnabledReform() & ReformOptions.ALLOW_MOVE)
                .isEqualTo(0);
        assertThat(
                        node.getReformOptions().getFlags()
                                & (ReformOptions.FLAG_SCALE_WITH_DISTANCE
                                        | ReformOptions.FLAG_ALLOW_SYSTEM_MOVEMENT))
                .isEqualTo(0);
        assertThat(movableComponent.reformEventConsumer).isNull();
        assertThat(((AndroidXrEntity) entity).reformEventConsumerMap).isEmpty();
    }

    @Test
    @Ignore
    public void movableComponent_canAttachAgainAfterDetach() {
        Entity entity = createTestEntity();
        assertThat(entity).isNotNull();
        MovableComponentImpl movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        /* anchorPlacement= */ ImmutableSet.of(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        entity.removeComponent(movableComponent);
        assertThat(entity.addComponent(movableComponent)).isTrue();
    }

    @Test
    @Ignore
    public void anchorable_updatesThePoseBasedOnPlanes() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        Entity entity = createTestEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ false,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags()).isEqualTo(0);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be 3 unit above the activity in order to rest on the plane.
        // It
        // is 3 units because the activity space is 1 unit below of the origin and the plane is 2
        // units
        // above it.
        Pose expectedPosition = new Pose(new Vector3(1f, 3f, 1f), new Quaternion(0f, 0f, 0f, 1f));
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isNull();

        // The panel shadow renderer should have no interaction.
        verify(panelShadowRenderer, never()).updatePanelPose(any(), any(), any());
        verify(panelShadowRenderer, never()).destroy();
        verify(panelShadowRenderer, never()).hidePlane();

        // The pose should have moved since the systemMovable is true.
        assertPose(entity.getPose(), expectedPosition);
    }

    @Test
    @Ignore
    public void anchorable_nullParent_updatesThePoseBasedOnPlanes() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        Entity entity = createTestEntity();
        entity.setParent(null);
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ false,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags()).isEqualTo(0);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be 3 unit above the activity in order to rest on the plane.
        // It
        // is 3 units because the activity space is 1 unit below of the origin and the plane is 2
        // units
        // above it.
        Pose expectedPosition = new Pose(new Vector3(1f, 3f, 1f), new Quaternion(0f, 0f, 0f, 1f));
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isNull();

        // The pose should have moved since the systemMovable is true.
        assertPose(entity.getPose(), expectedPosition);
    }

    @Test
    @Ignore
    public void anchorable_updatesPoseButDoesNotMove_ifNotSystemMovable() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        Entity entity = createTestEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ false,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be 3 unit above the activity in order to rest on the plane.
        // It
        // is 3 units because the activity space is 1 unit below of the origin and the plane is 2
        // units
        // above it.
        Pose expectedPosition = new Pose(new Vector3(1f, 3f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isNull();

        // The panel shadow renderer should have no interaction if it is not system movable.
        verify(panelShadowRenderer, never()).updatePanelPose(any(), any(), any());
        verify(panelShadowRenderer, never()).destroy();
        verify(panelShadowRenderer, never()).hidePlane();

        // The pose should not have moved since the systemMovable is false.
        assertPose(entity.getPose(), Pose.Identity);
    }

    @Test
    @Ignore
    public void anchorable_withNonActivityParent_updatesPoseBasedOnPlanesAndParent() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        // Create a parent entity whose pose is below the activity space pose.
        Entity parentEntity = createTestEntity();
        parentEntity.setPose(new Pose(new Vector3(0f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)));
        PanelEntity entity = createTestPanelEntity();
        entity.setParent(parentEntity);
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be 3 unit above the activity in order to rest on the plane.
        // It
        // is 3 units because the activity space is 1 unit below of the origin and the plane is 2
        // units
        // above it. Since the parent is 1 unit below the activity space, the expected position
        // should
        // be 4 units above the parent.
        Pose expectedPosition = new Pose(new Vector3(1f, 4f, 1f), new Quaternion(0f, 0f, 0f, 1f));
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isNull();

        // The pose should have moved since the systemMovable is true.
        assertPose(entity.getPose(), expectedPosition);
    }

    @Test
    @Ignore
    public void anchorableAndScaledParent_updatesThePoseBasedOnPlanes() {
        // Set the activity space pose to be 1 unit to the left of the origin. with a scale of 2.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 2f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane. This needs to be divided by the scale of the activity space.
        reformEvent.setProposedPosition(new Vec3(.5f, .5f, .5f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be 3 unit above the activity in order to rest on the plane.
        // It
        // is 1.5 units because the activity space is 1 unit below of the origin and the plane is 2
        // units above it and the activity space is scaled by 2.
        Pose expectedPosition =
                new Pose(new Vector3(.5f, 1.5f, .5f), new Quaternion(0f, 0f, 0f, 1f));
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isNull();
    }

    @Test
    @Ignore
    public void anchorable_withinAnchorDistance_setsAnchorEntity() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor.The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
    }

    @Test
    @Ignore
    public void anchorable_withinAnchorDistanceAboveAnchor_resetsPose() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 2 + half the MIN_PLANE_ANCHOR_DISTANCE above the origin. So
        // it
        // would be right above the plane.
        reformEvent.setProposedPosition(
                new Vec3(1f, 3f + MovableComponentImpl.MIN_PLANE_ANCHOR_DISTANCE / 2f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor.The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
    }

    @Test
    @Ignore
    public void anchorable_withIncorrectPlaneType_doesNotCreateAnchor() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        // Set the anchor placement to be a table.
        AnchorPlacement anchorPlacement =
                fakeRuntime.createAnchorPlacementForPlanes(
                        ImmutableSet.of(PlaneType.ANY), ImmutableSet.of(PlaneSemantic.TABLE));
        ImmutableSet<AnchorPlacement> anchorPlacementSet = ImmutableSet.of(anchorPlacement);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        anchorPlacementSet,
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be 3 unit above the activity in order to rest on the plane.
        // It
        // is 3 units because the activity space is 1 unit below of the origin and the plane is 2
        // units
        // above it. However, since the plane is not a table plane, the anchor should not be
        // created.
        Pose expectedPosition = new Pose(new Vector3(1f, 3f, 1f), new Quaternion(0f, 0f, 0f, 1f));
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was not set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isNull();
    }

    @Test
    @Ignore
    public void anchorable_withinAnchorDistanceAndScale_setsAnchorEntityAndScales() {
        // Set the activity space pose to be 1 unit to the left of the OpenXR origin and add a scale
        // of
        // 2.
        float activityScale = 2f;
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), activityScale);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        Vector3 entityScale = new Vector3(1f, 3f, 5f);
        PanelEntity entity = createTestPanelEntity();
        entity.setScale(entityScale);
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane. This needs to be divided by the scale of the activity space.
        reformEvent.setProposedPosition(new Vec3(.5f, .5f, .5f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor. The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis. Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
        assertVector3(entity.getWorldSpaceScale(), entityScale.times(activityScale));
    }

    @Test
    @Ignore
    public void anchorable_noPlanes_keepsProposedPose() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of());

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be unchanged from the proposed event
        Pose expectedPosition = new Pose(new Vector3(1f, 1f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
    }

    @Test
    @Ignore
    public void anchorable_noPlaneData_keepsProposedPose() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        Plane plane = mock(Plane.class);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.getData(any())).thenReturn(null);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be unchanged from the proposed event
        Pose expectedPosition = new Pose(new Vector3(1f, 1f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertThat(moveEventListener.callCount).isEqualTo(1);
    }

    @Test
    @Ignore
    public void anchorable_outsideExtents_keepsProposedPose() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        Plane plane = mock(Plane.class);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(5f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // The expected position should be unchanged from the proposed event
        Pose expectedPosition = new Pose(new Vector3(1f, 1f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertThat(moveEventListener.callCount).isEqualTo(1);
    }

    @Test
    @Ignore
    public void anchorable_resetsToActivityPoseAfterAnchoring() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor.The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());

        // Put the proposed position at 4 above the origin so it would be off the plane. It should
        // reset to the activity space pose and rotation.
        reformEvent.setProposedPosition(new Vec3(1f, 4f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving to (1, 4, 1) relative to the activity space. This should pull the entity away from
        // the
        // anchor and it should be reparented to the activity space.
        expectedPosition = new Pose(new Vector3(1f, 4f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertThat(moveEventListener.callCount).isEqualTo(2);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertPose(entity.getPose(), expectedPosition);
        // Check that parent was updated to the activity space.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(activitySpaceImpl);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
    }

    @Test
    @Ignore
    public void anchorable_resetsAndScaleToActivityPoseAfterAnchoring() {
        // Set the activity space pose to be 1 unit to the left of the OpenXR origin and add a scale
        // of
        // 2.
        float activityScale = 2f;
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), activityScale);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        Vector3 entityScale = new Vector3(1f, 3f, 5f);
        PanelEntity entity = createTestPanelEntity();
        entity.setScale(entityScale);
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane. This needs to be divided by the scale of the activity space.
        reformEvent.setProposedPosition(new Vec3(.5f, .5f, .5f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. Which results in an updated pose of (0, 0, 1)
        // relative to the anchor. The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis. Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertPose(entity.getPose(), expectedPosition);
        assertVector3(entity.getScale(), entityScale.times(activityScale));
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
        assertVector3(entity.getWorldSpaceScale(), entityScale.times(activityScale));

        // Put the proposed position at 4 above the activity space so it would be off the plane. It
        // should reset to the activity space pose and rotation.
        reformEvent.setProposedPosition(new Vec3(1f, 4f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving to (1, 4, 1) relative to the activity space. This should pull the entity away from
        // the
        // anchor and it should be reparented to the activity space.
        expectedPosition = new Pose(new Vector3(1f, 4f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertThat(moveEventListener.callCount).isEqualTo(2);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertPose(entity.getPose(), expectedPosition);
        // Check that parent was updated to the activity space.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(activitySpaceImpl);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
        // Check that the scale was updated to the original scale.
        assertVector3(entity.getScale(), entityScale);
    }

    @Test
    @Ignore
    public void anchorableChildOfEntity_resetsToActivityPoseAfterAnchoring() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        // Create a parent entity whose pose is below the activity space pose.
        Entity parentEntity = createTestEntity();
        parentEntity.setPose(new Pose(new Vector3(0f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)));
        PanelEntity entity = createTestPanelEntity();
        entity.setParent(parentEntity);

        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1 above the origin. It would need to move up 1 unit to be on
        // the
        // plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor.The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());

        // Put the proposed position at 4 above the origin so it would be off the plane. It should
        // reset to the activity space pose and rotation.
        reformEvent.setProposedPosition(new Vec3(1f, 4f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving to (1, 4, 1) relative to the activity space. This should pull the entity away from
        // the
        // anchor and it should be reparented to the activity space not the original parent..
        expectedPosition = new Pose(new Vector3(1f, 4f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertThat(moveEventListener.callCount).isEqualTo(2);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        assertPose(entity.getPose(), expectedPosition);
        // Check that parent was updated to the activity space.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(activitySpaceImpl);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
    }

    @Test
    @Ignore
    public void anchorable_shouldDispose_disposesAnchorAfterUnparenting() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ true,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor.The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
        // Cache the anchor entity.
        Entity anchorEntity = moveEventListener.lastMoveEvent.updatedParent;

        // Put the proposed position at 4 above the origin so it would be off the plane. It should
        // reset to the activity space pose and rotation.
        reformEvent.setProposedPosition(new Vec3(1f, 4f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving to (1, 4, 1) relative to the activity space. This should pull the entity away from
        // the
        // anchor and it should be reparented to the activity space.
        expectedPosition = new Pose(new Vector3(1f, 4f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertThat(moveEventListener.callCount).isEqualTo(2);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(activitySpaceImpl);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());

        // Verify that the anchor entity was disposed by checking that it is no longer in the entity
        // manager.
        assertThat(entityManager.getEntityForNode(((AndroidXrEntity) anchorEntity).getNode()))
                .isNull();
    }

    @Test
    @Ignore
    public void anchorable_shouldDispose_doeNotDisposeIfAnchorHasChildren() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        Anchor anchor = mock(Anchor.class);
        IBinder sharedAnchorToken = Mockito.mock(IBinder.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));
        when(plane.createAnchor(any(), any())).thenReturn(anchor);
        when(anchor.getAnchorToken()).thenReturn(sharedAnchorToken);

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.VERTICAL.intValue,
                        Plane.Label.WALL.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ true,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ true,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();
        TestMoveEventListener moveEventListener = new TestMoveEventListener();
        FakeScheduledExecutorService executorService = new FakeScheduledExecutorService();

        // Add the move event listener and the anchored event listener.
        movableComponent.addMoveEventListener(executorService, moveEventListener);
        // The reform options for parenting and moving should not be set when it is anchorable.
        assertThat(node.getReformOptions().getFlags())
                .isEqualTo(ReformOptions.FLAG_SCALE_WITH_DISTANCE);
        assertThat(node.getReformOptions().getEventCallback()).isNotNull();
        assertThat(node.getReformOptions().getEventExecutor()).isNotNull();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1  above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving from (1, 3, 1) relative to the activity space to be relative to the anchor which
        // is
        // (1, 3, 0) relative to the activity space. which results in an updated pose of (0, 0, 1)
        // relative to the anchor.The (-0.707f, 0f, 0f, 0.707f) Quaternion represents a 90 degree
        // rotation around the x-axis Which is expected when the panel is rotated into the plane's
        // reference space.
        Pose expectedPosition =
                new Pose(new Vector3(0f, 0f, 1f), new Quaternion(-0.707f, 0f, 0f, 0.707f));

        assertThat(moveEventListener.callCount).isEqualTo(1);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isInstanceOf(AnchorEntity.class);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());
        // Cache the anchor entity.
        Entity anchorEntity = moveEventListener.lastMoveEvent.updatedParent;

        Entity child = createTestEntity();
        anchorEntity.addChild(child);

        // Put the proposed position at 4 above the origin so it would be off the plane. It should
        // reset to the activity space pose and rotation.
        reformEvent.setProposedPosition(new Vec3(1f, 4f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();
        assertThat(executorService.hasNext()).isTrue();
        executorService.runAll();

        // Moving to (1, 4, 1) relative to the activity space. This should pull the entity away from
        // the
        // anchor and it should be reparented to the activity space.
        expectedPosition = new Pose(new Vector3(1f, 4f, 1f), new Quaternion(0f, 0f, 0f, 1f));

        assertThat(moveEventListener.callCount).isEqualTo(2);
        assertPose(moveEventListener.lastMoveEvent.currentPose, expectedPosition);
        // Check that the anchor entity was set.
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(activitySpaceImpl);
        assertThat(moveEventListener.lastMoveEvent.updatedParent).isEqualTo(entity.getParent());

        // Verify that the anchor entity wasn't disposed by checking that it is in the entity
        // manager.
        assertThat(entityManager.getEntityForNode(((AndroidXrEntity) anchorEntity).getNode()))
                .isEqualTo(anchorEntity);
    }

    @Test
    @Ignore
    public void anchorablePanelEntity_nearPlane_rendersShadow() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ false,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 1 above the origin. so it would need to move up 1 unit to
        // be on the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();

        // Since it is by the plane a call should be made to the panel shadow renderer.
        verify(panelShadowRenderer)
                .updatePanelPose(
                        new Pose(new Vector3(0f, 2f, 1f), new Quaternion(0f, 0f, 0f, 1f)),
                        RuntimeUtils.fromPerceptionPose(perceptionPose),
                        (PanelEntityImpl) entity);
    }

    @Test
    @Ignore
    public void anchorablePanelEntity_awayFromPlane_hidesShadow() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ false,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Put the proposed position at 5 above the origin. so it is far away from the plane.
        reformEvent.setProposedPosition(new Vec3(1f, 5f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_ONGOING);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();

        // Since it is by the plane a call should be made to the panel shadow renderer.
        verify(panelShadowRenderer).hidePlane();
    }

    @Test
    @Ignore
    public void anchorablePanelEntity_endMovement_callsDestroy() {
        // Set the activity space pose to be 1 unit to the left of the origin.
        setActivitySpacePose(
                new Pose(new Vector3(-1f, -1f, 0f), new Quaternion(0f, 0f, 0f, 1f)), 1f);
        Session session = Mockito.mock(Session.class);
        Plane plane = mock(Plane.class);
        when(perceptionLibrary.getSession()).thenReturn(session);
        when(session.getAllPlanes()).thenReturn(ImmutableList.of(plane));

        // Create a perception plane that is 2 units above the origin.
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                new androidx.xr.scenecore.impl.perception.Pose(0f, 2f, 0f, 0f, 0f, 0f, 1f);
        PlaneData planeData =
                new PlaneData(
                        perceptionPose,
                        1f,
                        1f,
                        Plane.Type.HORIZONTAL_UPWARD_FACING.intValue,
                        Plane.Label.FLOOR.intValue);
        when(plane.getData(any())).thenReturn(planeData);

        PanelEntity entity = createTestPanelEntity();
        // Set anchorPlacement to any plane.
        MovableComponent movableComponent =
                new MovableComponentImpl(
                        /* systemMovable= */ true,
                        /* scaleInZ= */ false,
                        createAnyAnchorPlacement(),
                        /* shouldDisposeParentAnchor= */ false,
                        perceptionLibrary,
                        fakeExtensions,
                        activitySpaceImpl,
                        activitySpaceRoot,
                        perceptionSpaceActivityPose,
                        entityManager,
                        panelShadowRenderer,
                        fakeExecutor);
        assertThat(movableComponent).isNotNull();
        assertThat(entity.addComponent(movableComponent)).isTrue();
        FakeNode node = (FakeNode) ((AndroidXrEntity) entity).getNode();

        FakeReformEvent reformEvent = new FakeReformEvent();

        // Set the reform state to end so that the plane shadow gets destroyed.
        reformEvent.setProposedPosition(new Vec3(1f, 1f, 1f));
        reformEvent.setType(ReformEvent.REFORM_TYPE_MOVE);
        reformEvent.setState(ReformEvent.REFORM_STATE_END);
        node.getReformOptions()
                .getEventExecutor()
                .execute(() -> node.getReformOptions().getEventCallback().accept(reformEvent));
        assertThat(fakeExecutor.hasNext()).isTrue();
        fakeExecutor.runAll();

        // Since it is by the plane a call should be made to the panel shadow renderer.
        verify(panelShadowRenderer).destroy();
    }

    static class TestMoveEventListener implements MoveEventListener {
        int callCount = 0;
        MoveEvent lastMoveEvent = null;

        @Override
        public void onMoveEvent(MoveEvent event) {
            lastMoveEvent = event;
            callCount++;
        }
    }
}
