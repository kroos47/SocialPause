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
    private TextView countdown;
    BlockOverlay(AccessibilityService service){this.service=service;}
    boolean visible(){return view!=null;}
    String blockedPackage(){return blockedPackage;}
    void show(String pkg,RulesEngine e) {
        if(visible())return;
        // Leave the social app immediately, even if adding the explanation window fails.
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        blockedPackage=pkg;Design d=new Design(service);
        LinearLayout root=d.column();root.setBackgroundColor(0xFF233F35);root.setPadding(d.dp(24),d.dp(40),d.dp(24),d.dp(32));
        TextView brand=d.text("SOCIALPAUSE",12,0xFFDDEED2,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        ScrollView area=new ScrollView(service);area.setFillViewport(true);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout center=d.column();center.setGravity(Gravity.CENTER);center.setPadding(0,d.dp(24),0,d.dp(24));area.addView(center);
        LinearLayout card=d.card(d.background,24);
        LinearLayout icon=d.row();icon.setGravity(Gravity.CENTER);icon.setBackground(d.shape(d.mint,24));icon.addView(d.icon(e.mode(AppController.wall(),AppController.elapsed())==RulesEngine.Mode.COOLDOWN?"clock":"pause",d.ink),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(d.dp(72),d.dp(72));iconLp.gravity=Gravity.CENTER;card.addView(icon,iconLp);
        boolean cooldown=e.mode(AppController.wall(),AppController.elapsed())==RulesEngine.Mode.COOLDOWN;
        d.add(card,d.text(cooldown?"Social time limit reached":"10-minute limit reached",28,d.ink,true),24);
        String message=cooldown ? (e.total()==0?"Your lunch break is complete. Social apps are resting for the next hour.":"You've used your 20-minute social allowance. Try again after cooldown.")
                :"Your time for "+AppController.label(service,pkg)+" is up. Other selected apps can use the remaining "+AppController.duration(RulesEngine.TOTAL_LIMIT-e.total())+" shared allowance.";
        d.add(card,d.text(message,16,d.muted,false),16);
        d.add(card,d.button("Go to phone Home",true,this::goHome),36);
        countdown=d.text("",12,d.muted,false);countdown.setGravity(Gravity.CENTER);d.add(card,countdown,14);center.addView(card,new LinearLayout.LayoutParams(-1,-2));
        TextView footer=d.text("Take a breath. Your day is waiting.",14,0xFFDDEED2,false);footer.setGravity(Gravity.CENTER);root.addView(footer);
        root.setFocusableInTouchMode(true);root.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)goHome();return true;}return false;});
        root.setOnApplyWindowInsetsListener((v,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(d.dp(24)+bars.left,d.dp(32)+bars.top,d.dp(24)+bars.right,d.dp(24)+bars.bottom);return insets;});
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
        params.setTitle("SocialPause limit reached");
        try { service.getSystemService(WindowManager.class).addView(root,params);view=root;root.requestFocus();dismissAt=SystemClock.elapsedRealtime()+5000;handler.post(tick); }
        catch(RuntimeException unavailable){view=null;blockedPackage=null;service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);}
    }
    private final Runnable tick=new Runnable(){@Override public void run(){if(view==null)return;long left=dismissAt-SystemClock.elapsedRealtime();if(left<=0){goHome();return;}countdown.setText(service.getString(R.string.returning_home,(left+999)/1000));handler.postDelayed(this,250);}};
    void goHome(){service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);dismiss();}
    void dismiss(){handler.removeCallbacksAndMessages(null);if(view!=null){try{service.getSystemService(WindowManager.class).removeView(view);}catch(IllegalArgumentException ignored){/* Already removed by system. */}view=null;}blockedPackage=null;}
}
