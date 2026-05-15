2026-05-14 19:50:39.207 20473-20473 VRI[MainActivity]       com.example.picsearch                V  [ANR Warning]Input routeing takes more than 6000ms since 1970-01-01 08:00:00.000, this = com.mediatek.view.impl.ViewDebugManagerImpl@16727a2
2026-05-14 19:50:39.208 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$NativePostImeInputStage@c2000c0 at 2026-05-14 19:50:39.195
2026-05-14 19:50:39.209 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$ViewPostImeInputStage@e5b01f9 at 2026-05-14 19:50:39.195
2026-05-14 19:50:39.210 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$EarlyPostImeInputStage@883243 at 2026-05-14 19:50:39.195
2026-05-14 19:50:39.211 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$SyntheticInputStage@7f1af9f at 2026-05-14 19:50:39.202
2026-05-14 19:50:39.219 20473-20473 VRI[MainActivity]       com.example.picsearch                V  [ANR Warning]Input routeing takes more than 6000ms since 1970-01-01 08:00:00.000, this = com.mediatek.view.impl.ViewDebugManagerImpl@16727a2
2026-05-14 19:50:39.220 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$NativePostImeInputStage@c2000c0 at 2026-05-14 19:50:39.212
2026-05-14 19:50:39.222 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$ViewPostImeInputStage@e5b01f9 at 2026-05-14 19:50:39.212
2026-05-14 19:50:39.222 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$EarlyPostImeInputStage@883243 at 2026-05-14 19:50:39.212
2026-05-14 19:50:39.223 20473-20473 VRI[MainActivity]       com.example.picsearch                V  Input event delivered to android.view.ViewRootImpl$SyntheticInputStage@7f1af9f at 2026-05-14 19:50:39.215
2026-05-14 19:50:41.194 20473-20619 CLIP_DEBUG              com.example.picsearch                D  Token IDs: 101, 6028, 4143, 7649, 102, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
2026-05-14 19:50:41.286 20473-20619 CLIP_DEBUG              com.example.picsearch                D  Text feat before L2 [0..4]: -1.439334 -1.005768 0.426583 1.351734 -0.727285
2026-05-14 19:50:41.286 20473-20619 CLIP_DEBUG              com.example.picsearch                D  Text feat after L2 [0..4]: -0.041231 -0.028811 0.012220 0.038721 -0.020834, dim=1024
2026-05-14 19:50:41.378 20473-20473 AndroidRuntime          com.example.picsearch                D  Shutting down VM
2026-05-14 19:50:41.381 20473-20473 AndroidRuntime          com.example.picsearch                E  FATAL EXCEPTION: main (Fix with AI)
Process: com.example.picsearch, PID: 20473
java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed. One of the common reasons is nesting layouts like LazyColumn and Column(Modifier.verticalScroll()). If you want to add a header before the list of items please add a header as a separate item() before the main items() inside the LazyColumn scope. There could be other reasons for this to happen: your ComposeView was added into a LinearLayout with some weight, you applied Modifier.wrapContentSize(unbounded = true) or wrote a custom layout. Please try to remove the source of infinite constraints in the hierarchy above the scrolling container.
at androidx.compose.foundation.internal.InlineClassHelperKt.throwIllegalStateException(InlineClassHelper.kt:26)
at androidx.compose.foundation.CheckScrollableContainerConstraintsKt.checkScrollableContainerConstraints-K40F9xA(CheckScrollableContainerConstraints.kt:59)
at androidx.compose.foundation.lazy.grid.LazyGridKt$rememberLazyGridMeasurePolicy$1$1.measure-0kLqBqw(LazyGrid.kt:195)
at androidx.compose.foundation.lazy.layout.LazyLayoutKt$LazyLayout$2.invoke$lambda$8$lambda$7(LazyLayout.kt:141)
at androidx.compose.foundation.lazy.layout.LazyLayoutKt$LazyLayout$2.$r8$lambda$AuADrFew1M7xDLhV5Co4CIX8-dw(Unknown Source:0)
at androidx.compose.foundation.lazy.layout.LazyLayoutKt$LazyLayout$2$$ExternalSyntheticLambda2.invoke(D8$$SyntheticClass:0)
at androidx.compose.ui.layout.LayoutNodeSubcompositionsState$createMeasurePolicy$1.measure-3p2s80s(SubcomposeLayout.kt:914)
at androidx.compose.ui.node.InnerNodeCoordinator.measure-BRTryo0(InnerNodeCoordinator.kt:128)
at androidx.compose.ui.graphics.SimpleGraphicsLayerModifier.measure-3p2s80s(GraphicsLayerModifier.kt:794)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.foundation.lazy.layout.LazyLayoutBeyondBoundsModifierNode.measure-3p2s80s(LazyLayoutBeyondBoundsModifierLocal.kt:118)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.foundation.layout.PaddingNode.measure-3p2s80s(Padding.kt:406)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.foundation.layout.PaddingNode.measure-3p2s80s(Padding.kt:406)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.ui.node.MeasurePassDelegate$performMeasureBlock$1.invoke(MeasurePassDelegate.kt:173)
at androidx.compose.ui.node.MeasurePassDelegate$performMeasureBlock$1.invoke(MeasurePassDelegate.kt:172)
at androidx.compose.runtime.snapshots.Snapshot$Companion.observe(Snapshot.kt:502)
at androidx.compose.runtime.snapshots.SnapshotStateObserver$ObservedScopeMap.observe(SnapshotStateObserver.kt:464)
at androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads(SnapshotStateObserver.kt:248)
at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(OwnerSnapshotObserver.kt:124)
at androidx.compose.ui.node.OwnerSnapshotObserver.observeMeasureSnapshotReads$ui_release(OwnerSnapshotObserver.kt:107)
at androidx.compose.ui.node.MeasurePassDelegate.performMeasure-BRTryo0$ui_release(MeasurePassDelegate.kt:426)
at androidx.compose.ui.node.MeasurePassDelegate.remeasure-BRTryo0(MeasurePassDelegate.kt:477)
at androidx.compose.ui.node.MeasurePassDelegate.measure-BRTryo0(MeasurePassDelegate.kt:454)
at androidx.compose.foundation.layout.RowColumnMeasurePolicyKt.measure(RowColumnMeasurePolicy.kt:126)
at androidx.compose.foundation.layout.RowColumnMeasurePolicyKt.measure$default(RowColumnMeasurePolicy.kt:77)
at androidx.compose.foundation.layout.ColumnMeasurePolicy.measure-3p2s80s(Column.kt:205)
2026-05-14 19:50:41.384 20473-20473 AndroidRuntime          com.example.picsearch                E  	at androidx.compose.ui.node.InnerNodeCoordinator.measure-BRTryo0(InnerNodeCoordinator.kt:128) (Fix with AI)
at androidx.compose.foundation.ScrollNode.measure-3p2s80s(Scroll.kt:415)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.ui.graphics.SimpleGraphicsLayerModifier.measure-3p2s80s(GraphicsLayerModifier.kt:794)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.foundation.layout.FillNode.measure-3p2s80s(Size.kt:721)
at androidx.compose.ui.node.LayoutModifierNodeCoordinator.measure-BRTryo0(LayoutModifierNodeCoordinator.kt:190)
at androidx.compose.ui.node.MeasurePassDelegate$performMeasureBlock$1.invoke(MeasurePassDelegate.kt:173)
at androidx.compose.ui.node.MeasurePassDelegate$performMeasureBlock$1.invoke(MeasurePassDelegate.kt:172)
at androidx.compose.runtime.snapshots.Snapshot$Companion.observe(Snapshot.kt:2495)
at androidx.compose.runtime.snapshots.SnapshotStateObserver$ObservedScopeMap.observe(SnapshotStateObserver.kt:464)
at androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads(SnapshotStateObserver.kt:248)
at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(OwnerSnapshotObserver.kt:124)
at androidx.compose.ui.node.OwnerSnapshotObserver.observeMeasureSnapshotReads$ui_release(OwnerSnapshotObserver.kt:107)
at androidx.compose.ui.node.MeasurePassDelegate.performMeasure-BRTryo0$ui_release(MeasurePassDelegate.kt:426)
at androidx.compose.ui.node.MeasurePassDelegate.remeasure-BRTryo0(MeasurePassDelegate.kt:477)
at androidx.compose.ui.node.LayoutNode.remeasure-_Sx5XlM$ui_release(LayoutNode.kt:1283)
at androidx.compose.ui.node.LayoutNode.remeasure-_Sx5XlM$ui_release$default(LayoutNode.kt:1276)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.doRemeasure-sdFAvZA(MeasureAndLayoutDelegate.kt:380)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.remeasureAndRelayoutIfNeeded(MeasureAndLayoutDelegate.kt:595)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.onlyRemeasureIfPending(MeasureAndLayoutDelegate.kt:689)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.forceMeasureTheSubtreeInternal(MeasureAndLayoutDelegate.kt:716)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.forceMeasureTheSubtreeInternal(MeasureAndLayoutDelegate.kt:723)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.forceMeasureTheSubtree(MeasureAndLayoutDelegate.kt:680)
at androidx.compose.ui.platform.AndroidComposeView.forceMeasureTheSubtree(AndroidComposeView.android.kt:1593)
at androidx.compose.ui.node.Owner.forceMeasureTheSubtree$default(Owner.kt:256)
at androidx.compose.ui.node.MeasurePassDelegate.remeasure-BRTryo0(MeasurePassDelegate.kt:492)
at androidx.compose.ui.node.LayoutNode.remeasure-_Sx5XlM$ui_release(LayoutNode.kt:1283)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.doRemeasure-sdFAvZA(MeasureAndLayoutDelegate.kt:378)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.remeasureOnly(MeasureAndLayoutDelegate.kt:658)
at androidx.compose.ui.node.MeasureAndLayoutDelegate.measureOnly(MeasureAndLayoutDelegate.kt:446)
at androidx.compose.ui.platform.AndroidComposeView.onMeasure(AndroidComposeView.android.kt:1671)
at android.view.View.measure(View.java:28195)
at androidx.compose.ui.platform.AbstractComposeView.internalOnMeasure$ui_release(ComposeView.android.kt:299)
at androidx.compose.ui.platform.AbstractComposeView.onMeasure(ComposeView.android.kt:286)
at android.view.View.measure(View.java:28195)
at android.view.ViewGroup.measureChildWithMargins(ViewGroup.java:7159)
at android.widget.FrameLayout.onMeasure(FrameLayout.java:194)
at android.view.View.measure(View.java:28195)
at android.view.ViewGroup.measureChildWithMargins(ViewGroup.java:7159)
at android.widget.LinearLayout.measureChildBeforeLayout(LinearLayout.java:1608)
at android.widget.LinearLayout.measureVertical(LinearLayout.java:878)
2026-05-14 19:50:41.385 20473-20473 AndroidRuntime          com.example.picsearch                E  	at android.widget.LinearLayout.onMeasure(LinearLayout.java:721) (Fix with AI)
at android.view.View.measure(View.java:28195)
at android.view.ViewGroup.measureChildWithMargins(ViewGroup.java:7159)
at android.widget.FrameLayout.onMeasure(FrameLayout.java:194)
at com.android.internal.policy.DecorView.onMeasure(DecorView.java:837)
at android.view.View.measure(View.java:28195)
at android.view.ViewRootImpl.performMeasure(ViewRootImpl.java:4822)
at android.view.ViewRootImpl.measureHierarchy(ViewRootImpl.java:3315)
at android.view.ViewRootImpl.performTraversals(ViewRootImpl.java:3676)
at android.view.ViewRootImpl.doTraversal(ViewRootImpl.java:3014)
at android.view.ViewRootImpl$TraversalRunnable.run(ViewRootImpl.java:10526)
at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1671)
at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1680)
at android.view.Choreographer.doCallbacks(Choreographer.java:1191)
at android.view.Choreographer.doFrame(Choreographer.java:1063)
at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1650)
at android.os.Handler.handleCallback(Handler.java:958)
at android.os.Handler.dispatchMessage(Handler.java:99)
at android.os.Looper.loopOnce(Looper.java:222)
at android.os.Looper.loop(Looper.java:314)
at android.app.ActivityThread.main(ActivityThread.java:8680)
at java.lang.reflect.Method.invoke(Native Method)
at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:565)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1081)
2026-05-14 19:50:41.396 20473-20473 ScoutUtils              com.example.picsearch                W  Failed to mkdir /data/miuilog/stability/memleak/heapdump/
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81532): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81533): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81534): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81535): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81536): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81537): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81538): avc:  denied  { getattr } for  path="/data/miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.389 20473-20473 ample.picsearch         com.example.picsearch                W  type=1400 audit(0.0:81539): avc:  denied  { search } for  name="miuilog" dev="dm-57" ino=370 scontext=u:r:untrusted_app:s0:c144,c257,c512,c768 tcontext=u:object_r:data_log_file:s0 tclass=dir permissive=0 app=com.example.picsearch
2026-05-14 19:50:41.446 20473-20473 Process                 com.example.picsearch                I  Process is going to kill itself! (Fix with AI)
java.lang.Exception
at android.os.Process.killProcess(Process.java:1346)
at com.android.internal.os.RuntimeInit$KillApplicationHandler.uncaughtException(RuntimeInit.java:178)
at java.lang.ThreadGroup.uncaughtException(ThreadGroup.java:1071)
at java.lang.ThreadGroup.uncaughtException(ThreadGroup.java:1066)
at java.lang.Thread.dispatchUncaughtException(Thread.java:2306)
2026-05-14 19:50:41.447 20473-20473 Process                 com.example.picsearch                I  Sending signal. PID: 20473 SIG: 9
---------------------------- PROCESS ENDED (20473) for package com.example.picsearch ----------------------------