/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.tests.customtiles.unit;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import android.text.TextUtils;
import android.util.Log;

import lineageos.app.LineageContextConstants;
import lineageos.app.LineageStatusBarManager;
import lineageos.app.CustomTile;
import lineageos.app.CustomTileListenerService;
import lineageos.app.ILineageStatusBarManager;
import org.lineageos.tests.R;

import java.util.concurrent.CountDownLatch;

public class LineageStatusBarManagerTest extends AndroidTestCase {
    private static final String TAG = LineageStatusBarManagerTest.class.getSimpleName();
    private static final int COUNTDOWN = 1;
    private LineageStatusBarManager mLineageStatusBarManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Only run this if we support lineage status bar service
        org.junit.Assume.assumeTrue(mContext.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.STATUSBAR));
        mLineageStatusBarManager = LineageStatusBarManager.getInstance(mContext);
    }

    @SmallTest
    public void testManagerExists() {
        assertNotNull(mLineageStatusBarManager);
    }

    @SmallTest
    public void testManagerServiceIsAvailable() {
        ILineageStatusBarManager ilineageHardwareManagerService = mLineageStatusBarManager.getService();
        assertNotNull(ilineageHardwareManagerService);
    }

    @MediumTest
    public void testCustomTileListenerServiceRegisterAndUnregisterAsSystemService() {
        CustomTileListenerService customTileListenerService = new CustomTileListenerService();
        registerCustomTileListenerService(customTileListenerService);
        unregisterCustomTileListenerService(customTileListenerService);
    }

    @MediumTest
    public void testCustomTileListenerServiceOnListenerConnected() {
        final CountDownLatch signal = new CountDownLatch(COUNTDOWN);
        CustomTileListenerService customTileListenerService =
                new CustomTileListenerService() {
                    @Override
                    public IBinder onBind(Intent intent) {
                        Log.d(TAG, "Bound");
                        signal.countDown();
                        return super.onBind(intent);
                    }
                    @Override
                    public void onListenerConnected() {
                        Log.d(TAG, "Connected");
                        super.onListenerConnected();
                        signal.countDown();
                    }
                };

        registerCustomTileListenerService(customTileListenerService);

        // Lock
        try {
            signal.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }

        unregisterCustomTileListenerService(customTileListenerService);
    }

    @MediumTest
    public void testCustomTileListenerServiceOnCustomTilePosted() {
        final CustomTile expectedCustomTile = createSampleCustomTile();
        final UserHandle expectedUserHandle = new UserHandle(UserHandle.myUserId());

        final CountDownLatch signal = new CountDownLatch(COUNTDOWN);
        CustomTileListenerService customTileListenerService =
                new CustomTileListenerService() {
                    @Override
                    public void onListenerConnected() {
                        super.onListenerConnected();
                        Log.d(TAG, "Connected");
                        // publish
                        mLineageStatusBarManager.publishTileAsUser(null, 1337, expectedCustomTile,
                                expectedUserHandle);
                    }

                    @Override
                    public void onCustomTilePosted(lineageos.app.StatusBarPanelCustomTile sbc) {
                        super.onCustomTilePosted(sbc);
                        Log.d(TAG, "Posted " + sbc.getCustomTile());
                        if (TextUtils.equals(expectedCustomTile.label, sbc.getCustomTile().label)) {
                            signal.countDown();
                        }
                    }
                };

        registerCustomTileListenerService(customTileListenerService);

        // Lock
        try {
            signal.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }

        mLineageStatusBarManager.removeTileAsUser(null, 1337, expectedUserHandle);

        unregisterCustomTileListenerService(customTileListenerService);
    }

    @MediumTest
    public void testCustomTileListenerServiceOnCustomTileRemoved() {
        final CustomTile expectedCustomTile = createSampleCustomTile();

        final CountDownLatch signal = new CountDownLatch(COUNTDOWN);
        CustomTileListenerService customTileListenerService =
                new CustomTileListenerService() {
                    @Override
                    public void onListenerConnected() {
                        super.onListenerConnected();
                        Log.d(TAG, "Connected");
                        // publish as user
                        mLineageStatusBarManager.publishTile(1338, expectedCustomTile);
                    }

                    @Override
                    public void onCustomTilePosted(lineageos.app.StatusBarPanelCustomTile sbc) {
                        super.onCustomTilePosted(sbc);
                        Log.d(TAG, "Posted " + sbc.getCustomTile());
                        if (TextUtils.equals(expectedCustomTile.label, sbc.getCustomTile().label)) {
                            removeCustomTile(mContext.getPackageName(), null, 1338);
                        }
                    }

                    @Override
                    public void onCustomTileRemoved(lineageos.app.StatusBarPanelCustomTile sbc) {
                        super.onCustomTileRemoved(sbc);
                        Log.d(TAG, "Removed " + sbc.getCustomTile());
                        if (TextUtils.equals(expectedCustomTile.label, sbc.getCustomTile().label)) {
                            signal.countDown();
                        }
                    }
                };

        registerCustomTileListenerService(customTileListenerService);

        // Lock
        try {
            signal.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }

        unregisterCustomTileListenerService(customTileListenerService);
    }

    private CustomTile createSampleCustomTile() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        return new CustomTile.Builder(mContext)
                .setLabel("YOLO")
                .setIcon(R.drawable.ic_launcher)
                .setOnClickIntent(pendingIntent)
                .build();
    }

    private void registerCustomTileListenerService(
            CustomTileListenerService customTileListenerService) {
        try {
            Log.d(TAG, "Registering " + customTileListenerService
                    + " custom tile listener service");
            customTileListenerService.registerAsSystemService(mContext,
                    new ComponentName(mContext.getPackageName(),
                            LineageStatusBarManagerTest.this.getClass().getCanonicalName()),
                    UserHandle.USER_ALL);
        } catch (RemoteException e) {
            throw new AssertionError(e);
        }
    }

    private void unregisterCustomTileListenerService(CustomTileListenerService customTileListenerService) {
        try {
            Log.d(TAG, "Unregistering " + customTileListenerService
                    + " custom tile listener service");
            customTileListenerService.unregisterAsSystemService();
        } catch (RemoteException e) {
            throw new AssertionError(e);
        }
    }
}
