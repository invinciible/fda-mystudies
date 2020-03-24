/*
 * Copyright © 2017-2019 Harvard Pilgrim Health Care Institute (HPHCI) and its Contributors.
 * Copyright 2020 Google LLC
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * Funding Source: Food and Drug Administration (“Funding Agency”) effective 18 September 2014 as Contract no. HHSF22320140030I/HHSF22301006T (the “Prime Contract”).
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.harvard.utils;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import com.harvard.FDAApplication;
import com.harvard.R;
import com.harvard.offlinemodule.model.OfflineData;
import com.harvard.storagemodule.DBServiceSubscriber;
import com.harvard.studyappmodule.StudyModulePresenter;
import com.harvard.studyappmodule.events.ProcessResponseEvent;
import com.harvard.usermodule.UserModulePresenter;
import com.harvard.usermodule.event.UpdatePreferenceEvent;
import com.harvard.usermodule.webservicemodel.LoginData;
import com.harvard.webservicemodule.apihelper.ApiCall;
import com.harvard.webservicemodule.apihelper.ApiCallResponseServer;
import com.harvard.webservicemodule.events.RegistrationServerEnrollmentConfigEvent;
import com.harvard.webservicemodule.events.ResponseServerConfigEvent;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.HashMap;
import io.realm.Realm;
import io.realm.RealmResults;

public class ActiveTaskService extends Service
    implements ApiCall.OnAsyncRequestComplete, ApiCallResponseServer.OnAsyncRequestComplete {
  private int sec;
  private Thread t;
  private int UPDATE_USERPREFERENCE_RESPONSECODE = 102;
  private DBServiceSubscriber dbServiceSubscriber;
  private Realm mRealm;

  @Override
  public void onCreate() {
    super.onCreate();
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
    wakeLock.acquire();
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    if (intent != null)
      if (intent.getStringExtra("broadcast") != null
          && intent.getStringExtra("broadcast").equalsIgnoreCase("yes")) {
        startAlarm();
      } else if (intent.getStringExtra("SyncAdapter") != null) {
        mRealm = AppController.getRealmobj(this);
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        Notification notification =
            new NotificationCompat.Builder(this)
                .setContentTitle(getResources().getString(R.string.prject_name))
                .setTicker("Sync adapter")
                .setContentText("Syncing offline data")
                .setChannelId(FDAApplication.NOTIFICATION_CHANNEL_ID_SERVICE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setOngoing(true)
                .build();

        startForeground(102, notification);
        getPendingData();
      } else {
        try {
          sec = 0;
          startAlarm();
          Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
          Notification notification =
              new NotificationCompat.Builder(this)
                  .setContentTitle(getResources().getString(R.string.prject_name))
                  .setTicker(getResources().getString(R.string.fetal_kick_recorder_activity))
                  .setContentText(
                      getResources().getString(R.string.fetal_kick_recorder_activity_in_progress))
                  .setChannelId(FDAApplication.NOTIFICATION_CHANNEL_ID_SERVICE)
                  .setSmallIcon(R.mipmap.ic_launcher)
                  .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                  .setOngoing(true)
                  .build();

          startForeground(101, notification);

          Runnable r =
              new Runnable() {
                public void run() {
                  try {
                    while (!t.isInterrupted()) {
                      sec = sec + 1;
                      Intent i = new Intent("com.harvard.ActiveTask");
                      i.putExtra("sec", "" + sec);
                      sendBroadcast(i);
                      if (sec % 1800 == 0) {
                        NotificationCompat.Builder builder =
                            new NotificationCompat.Builder(ActiveTaskService.this);
                        builder
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setLargeIcon(
                                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                            .setContentTitle(getResources().getString(R.string.prject_name))
                            .setContentText(getString(R.string.activetaskremindertxt))
                            .setChannelId(FDAApplication.NOTIFICATION_CHANNEL_ID_SERVICE);

                        NotificationManager mNotificationManager =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        mNotificationManager.notify(sec, builder.build());
                      }

                      if (sec >= Integer.parseInt(intent.getStringExtra("remaining_sec"))) {
                        t.interrupt();
                        stopSelf();
                      }
                      t.sleep(1000);
                    }
                  } catch (Exception e) {
                    Logger.log(e);
                  }
                }
              };

          t = new Thread(r);
          t.start();
        } catch (Exception e) {
          Logger.log(e);
        }
      }
    return Service.START_NOT_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    try {
      if (t != null) t.interrupt();
    } catch (Exception e) {
      Logger.log(e);
    }
  }

  private void startAlarm() {
    if (Build.VERSION.SDK_INT >= 23) {
      try {
        Calendar localCalendar = Calendar.getInstance();
        Intent intent1 = new Intent(this, AlarmService.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this, 0, intent1, 0);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE); // 21600000
        alarmManager.setAlarmClock(
            new AlarmManager.AlarmClockInfo(localCalendar.getTimeInMillis() + 180000, alarmIntent),
            alarmIntent);
      } catch (Exception e) {
        Logger.log(e);
      }
    }
  }

  private void getPendingData() {
    try {
      dbServiceSubscriber = new DBServiceSubscriber();

      RealmResults<OfflineData> results = dbServiceSubscriber.getOfflineData(mRealm);
      if (!results.isEmpty()) {
        for (int i = 0; i < results.size(); i++) {
          String httpMethod = results.get(i).getHttpMethod().toString();
          String url = results.get(i).getUrl().toString();
          String normalParam = results.get(i).getNormalParam().toString();
          String jsonObject = results.get(i).getJsonParam().toString();
          String serverType = results.get(i).getServerType().toString();
          updateServer(httpMethod, url, normalParam, jsonObject, serverType);
          break;
        }
      } else {
        dbServiceSubscriber.closeRealmObj(mRealm);
        stopSelf();
      }
    } catch (Exception e) {
      stopSelf();
      Logger.log(e);
    }
  }

  public void updateServer(
      String httpMethod,
      String url,
      String normalParam,
      String jsonObjectString,
      String serverType) {

    JSONObject jsonObject = null;
    try {
      jsonObject = new JSONObject(jsonObjectString);
    } catch (JSONException e) {
      Logger.log(e);
    }

    if (serverType.equalsIgnoreCase("registration")) {
      HashMap<String, String> header = new HashMap();
      header.put(
          "auth",
          AppController.getHelperSharedPreference()
              .readPreference(this, getResources().getString(R.string.auth), ""));
      header.put(
          "userId",
          AppController.getHelperSharedPreference()
              .readPreference(this, getResources().getString(R.string.userid), ""));

      UpdatePreferenceEvent updatePreferenceEvent = new UpdatePreferenceEvent();
      RegistrationServerEnrollmentConfigEvent RegistrationServerEnrollmentConfigEvent =
          new RegistrationServerEnrollmentConfigEvent(
              httpMethod,
              url,
              UPDATE_USERPREFERENCE_RESPONSECODE,
              this,
              LoginData.class,
              null,
              header,
              jsonObject,
              false,
              this);
      updatePreferenceEvent.setRegistrationServerEnrollmentConfigEvent(
          RegistrationServerEnrollmentConfigEvent);
      UserModulePresenter userModulePresenter = new UserModulePresenter();
      userModulePresenter.performUpdateUserPreference(updatePreferenceEvent);
    } else if (serverType.equalsIgnoreCase("response")) {
      ProcessResponseEvent processResponseEvent = new ProcessResponseEvent();
      ResponseServerConfigEvent responseServerConfigEvent =
          new ResponseServerConfigEvent(
              httpMethod,
              url,
              UPDATE_USERPREFERENCE_RESPONSECODE,
              this,
              LoginData.class,
              null,
              null,
              jsonObject,
              false,
              this);

      processResponseEvent.setResponseServerConfigEvent(responseServerConfigEvent);
      StudyModulePresenter studyModulePresenter = new StudyModulePresenter();
      studyModulePresenter.performProcessResponse(processResponseEvent);
    } else if (serverType.equalsIgnoreCase("wcp")) {
    }
  }

  @Override
  public <T> void asyncResponse(T response, int responseCode) {
    if (responseCode == UPDATE_USERPREFERENCE_RESPONSECODE) {
      dbServiceSubscriber.removeOfflineData(this);
      getPendingData();
    }
  }

  @Override
  public void asyncResponseFailure(int responseCode, String errormsg, String statusCode) {
    stopSelf();
  }

  @Override
  public <T> void asyncResponse(T response, int responseCode, String serverType) {
    if (responseCode == UPDATE_USERPREFERENCE_RESPONSECODE) {
      dbServiceSubscriber.removeOfflineData(this);
      getPendingData();
    }
  }

  @Override
  public <T> void asyncResponseFailure(
      int responseCode, String errormsg, String statusCode, T response) {
    stopSelf();
  }
}
