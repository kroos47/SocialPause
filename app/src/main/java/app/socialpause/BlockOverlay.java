package app.socialpause;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.socialpause.engine.RulesEngine;

/** Brief, touch-blocking explanation owned by the Accessibility service; never grants extra app time. */
final class BlockOverlay {
    private final AccessibilityService service;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private View view;
    private String blockedPackage;
    private long dismissAt;
    private TextView countdown,remaining;
    private RulesEngine engine;
    private boolean noAllowance;
    BlockOverlay(AccessibilityService service){this.service=service;}
    boolean visible(){return view!=null;}
    String blockedPackage(){return blockedPackage;}
    void show(String pkg,RulesEngine e) {
        if(visible()||!e.blocked(pkg,AppController.wall(),AppController.elapsed()))return;
        // Leave the social app immediately, even if adding the explanation window fails.
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        engine=e;blockedPackage=pkg;noAllowance=e.limit(pkg)==0;Design d=new Design(service);
        LinearLayout root=d.column();root.setBackgroundColor(0xEE14251F);root.setPadding(d.dp(24),d.dp(40),d.dp(24),d.dp(32));
        TextView brand=d.text("SOCIALPAUSE",12,0xFFDDEED2,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        ScrollView area=new ScrollView(service);area.setFillViewport(true);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout center=d.column();center.setGravity(Gravity.CENTER);center.setPadding(0,d.dp(24),0,d.dp(24));area.addView(center);
        LinearLayout card=d.card(d.surface,24);
        View icon=d.badge(pkg);LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(d.dp(48),d.dp(48));iconLp.gravity=Gravity.CENTER;card.addView(icon,iconLp);
        boolean lunch=e.mode(AppController.wall(),AppController.elapsed())==RulesEngine.Mode.LUNCH_COOLDOWN;
        boolean shared=e.mode(AppController.wall(),AppController.elapsed())==RulesEngine.Mode.SHARED_COOLDOWN;
        TextView title=d.text(noAllowance?AppController.label(service,pkg)+" has no allowance":lunch?"Time for a breather":shared?"Shared time limit reached":AppController.label(service,pkg)+"’s "+e.limit(pkg)/RulesEngine.MINUTE+"-minute limit reached",26,d.ink,true);title.setGravity(Gravity.CENTER);d.add(card,title,24);
        long left=e.cooldownRemaining(pkg,AppController.wall(),AppController.elapsed());
        TextView message=d.text(noAllowance?"This app has a 0-minute allowance. Lunch Break and Stop still allow free access.":lunch?"Your lunch break is complete. All selected apps are resting.":shared?"Your combined allowance is complete. All selected apps are resting.":"A little space for now. Other apps keep their own timers.",14,d.muted,false);message.setGravity(Gravity.CENTER);d.add(card,message,16);
        remaining=d.text(noAllowance?"Blocked":AppController.duration(left),40,d.ink,true);remaining.setGravity(Gravity.CENTER);d.add(card,remaining,24);
        if(!noAllowance){TextView unlock=d.text("Available again at "+AppController.at(AppController.wall()+left),13,d.muted,false);unlock.setGravity(Gravity.CENTER);d.add(card,unlock,8);}
        d.add(card,d.button("Go to phone Home",true,this::goHome),28);
        countdown=d.text("",12,d.muted,false);countdown.setGravity(Gravity.CENTER);d.add(card,countdown,14);center.addView(card,new LinearLayout.LayoutParams(-1,-2));
        TextView footer=d.text("Take a breath. Your day is waiting.",14,0xFFDDEED2,false);footer.setGravity(Gravity.CENTER);root.addView(footer);
        root.setFocusableInTouchMode(true);root.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)goHome();return true;}return false;});
        root.setOnApplyWindowInsetsListener((v,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(d.dp(24)+bars.left,d.dp(32)+bars.top,d.dp(24)+bars.right,d.dp(24)+bars.bottom);return insets;});
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
        params.setTitle("SocialPause limit reached");
        try { service.getSystemService(WindowManager.class).addView(root,params);view=root;root.requestFocus();dismissAt=SystemClock.elapsedRealtime()+5000;handler.post(tick); }
        catch(RuntimeException unavailable){view=null;blockedPackage=null;service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);}
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(view==null)return;
        long wall=AppController.wall(),elapsed=AppController.elapsed();
        if(!engine.blocked(blockedPackage,wall,elapsed)){dismiss();return;}
        long left=dismissAt-elapsed;if(left<=0){goHome();return;}
        remaining.setText(noAllowance?"Blocked":AppController.duration(engine.cooldownRemaining(blockedPackage,wall,elapsed)));
        countdown.setText(service.getString(R.string.returning_home,(left+999)/1000));handler.postDelayed(this,250);
    }};
    void goHome(){service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);dismiss();}
    void dismiss(){handler.removeCallbacksAndMessages(null);if(view!=null){try{service.getSystemService(WindowManager.class).removeView(view);}catch(IllegalArgumentException ignored){/* Already removed by system. */}view=null;}blockedPackage=null;}
}
