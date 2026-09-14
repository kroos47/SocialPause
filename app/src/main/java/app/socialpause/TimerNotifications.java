package app.socialpause;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import app.socialpause.engine.*;

/** One standard system notification; Samsung owns its final layout and live-chip eligibility. */
final class TimerNotifications {
    private final Context context;
    private final NotificationManager manager;
    private String previous="";
    TimerNotifications(Context c) {
        context=c;manager=c.getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel("timer","Social timers",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
    }
    private int appIcon(String pkg) {
        if("com.instagram.android".equals(pkg))return R.drawable.ic_instagram;
        if("com.twitter.android".equals(pkg))return R.drawable.ic_x;
        if("com.reddit.frontpage".equals(pkg))return R.drawable.ic_reddit;
        return R.drawable.ic_pause;
    }
    void update(RulesEngine e,boolean connected) {
        long wall=AppController.wall(),elapsed=AppController.elapsed();TimerPresentation p=TimerPresentation.of(e,connected,wall,elapsed);
        if(p.kind==TimerPresentation.Kind.HIDDEN || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {manager.cancel(10);previous="";return;}
        String label=p.app==null?"":AppController.label(context,p.app), title, detail;
        long shared=RulesEngine.TOTAL_LIMIT-e.total();
        switch(p.kind) {
            case APP -> {title=label;detail="Time remaining in "+label+" · Shared allowance "+AppController.duration(shared);}
            case SHARED -> {title="Social allowance";detail="Until all social apps are blocked · "+label+" has "+AppController.duration(e.remaining(p.app))+" app time left";}
            case PAUSED -> {title="Usage paused";detail="Shared allowance remaining · Resume by opening a selected app";}
            case LUNCH -> {title="Lunch break · unrestricted";detail="Cooldown starts at "+at(wall+p.remaining)+" · Fresh allowance at "+at(wall+p.remaining+RulesEngine.COOLDOWN);}
            default -> {title="Social apps blocked";detail="Cooldown remaining · Available again at "+at(wall+p.remaining);}
        }
        // A user may dismiss the system surface. Do not repeatedly repost it within the same cycle.
        String key=e.cycleId()+":"+p.kind+":"+p.app+":"+p.remaining/1000+":"+detail;
        if(key.equals(previous))return;previous=key;
        if(NotificationDismissReceiver.dismissed(context,e.cycleId()))return;
        String body=p.ticking()?detail:AppController.duration(p.remaining)+" · "+detail;
        int icon=p.kind==TimerPresentation.Kind.APP?appIcon(p.app):R.drawable.ic_pause;
        PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent dismiss=PendingIntent.getBroadcast(context,10,new Intent(context,NotificationDismissReceiver.class).setAction(NotificationDismissReceiver.ACTION).putExtra("cycle",e.cycleId()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(context,"timer").setSmallIcon(icon).setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open).setDeleteIntent(dismiss).setOngoing(true).setOnlyAlertOnce(true).setColor(0xFF195E4C).setCategory(Notification.CATEGORY_PROGRESS);
        if(Build.VERSION.SDK_INT>=36) {
            Notification.ProgressStyle style=new Notification.ProgressStyle().setProgressSegments(java.util.List.of(new Notification.ProgressStyle.Segment(100).setColor(0xFF195E4C)))
                    .setProgress(p.progress()).setProgressTrackerIcon(Icon.createWithResource(context,icon));
            b.setStyle(style);
        } else b.setStyle(new Notification.BigTextStyle().bigText(body)).setProgress(100,p.progress(),false);
        if(p.ticking()) {
            b.setWhen(wall+p.remaining).setUsesChronometer(true).setChronometerCountDown(true);
            Bundle extras=new Bundle();extras.putBoolean("android.requestPromotedOngoing",true);b.addExtras(extras);
        } else b.setShowWhen(false).setUsesChronometer(false);
        manager.notify(10,b.build());
    }
    private static String at(long wall){return Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"));}
}
