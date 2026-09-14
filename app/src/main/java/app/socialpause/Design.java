package app.socialpause;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Reusable visual tokens and native drawing primitives matching the supplied design. */
final class Design {
    final Context c;
    final boolean dark;
    final int background, surface, ink, muted, line, mint, accent, onAccent = Color.WHITE;
    Design(Context c) {
        this.c = c; dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        background = Color.parseColor(dark ? "#14251F" : "#F6F8F2");
        surface = Color.parseColor(dark ? "#223A31" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#E7F1E5" : "#1E3932");
        muted = Color.parseColor(dark ? "#B0C3B5" : "#66796E");
        line = Color.parseColor(dark ? "#3E5648" : "#DFE5DA");
        mint = Color.parseColor(dark ? "#314E35" : "#E0F0D3");
        accent = Color.parseColor("#195E4C");
    }
    int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false);
        return t;
    }
    LinearLayout column() { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l = new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    LinearLayout card(int color, int padding) { LinearLayout l = column(); l.setBackground(shape(color, 24)); l.setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); return l; }
    void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(top); parent.addView(child, lp);
    }
    void weighted(LinearLayout parent, View child) { parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1)); }
    Button button(String label, boolean filled, Runnable action) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setTextColor(filled ? onAccent : ink); b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setBackground(shape(filled ? accent : surface, 28)); b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setOnClickListener(v -> action.run()); return b;
    }
    TextView badge(String pkg) {
        String letter = pkg.equals("com.instagram.android") ? "I" : pkg.equals("com.twitter.android") ? "X" : pkg.equals("com.reddit.frontpage") ? "r" : AppController.label(c, pkg).substring(0,1).toUpperCase(Locale.getDefault());
        int color = Color.parseColor(pkg.equals("com.instagram.android") ? "#AD4379" : pkg.equals("com.reddit.frontpage") ? "#C85B37" : "#233B32");
        TextView v = text(letter, 22, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setBackground(shape(color, 12));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }
    static String usage(long millis) {
        long sec = Math.max(0, millis) / 1000, min = sec / 60;
        if (min >= 60) return min / 60 + "h " + min % 60 + "m";
        if (min > 0) return min + "m" + (sec % 60 == 0 ? "" : " " + sec % 60 + "s");
        return sec + "s";
    }
    Icon icon(String kind, int color) { return new Icon(c, kind, color); }
    static final class Icon extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final String kind; final Path path = new Path();
        public Icon(Context c) { this(c,"pause",0xFF195E4C); }
        Icon(Context c, String kind, int color) { super(c); this.kind = kind; p.setColor(color); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/24f, getHeight()/24f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch(kind) {
                case "home" -> { path.moveTo(3,11); path.lineTo(12,3); path.lineTo(21,11); path.lineTo(21,21); path.lineTo(15,21); path.lineTo(15,14); path.lineTo(9,14); path.lineTo(9,21); path.lineTo(3,21); path.close(); canvas.drawPath(path,p); }
                case "chart" -> { canvas.drawRect(4,13,8,21,p); canvas.drawRect(10,5,14,21,p); canvas.drawRect(16,9,20,21,p); }
                case "settings" -> { for(int y=6;y<=18;y+=6) canvas.drawLine(4,y,20,y,p); canvas.drawLine(9,3,9,9,p); canvas.drawLine(16,9,16,15,p); canvas.drawLine(11,15,11,21,p); }
                case "clock" -> { canvas.drawCircle(12,12,9,p); canvas.drawLine(12,6,12,12,p); canvas.drawLine(12,12,16,15,p); }
                case "moon" -> { path.moveTo(14,3); path.cubicTo(0,1,1,24,17,20); path.cubicTo(8,19,7,9,14,3); canvas.drawPath(path,p); }
                case "chevron" -> { path.moveTo(9,5); path.lineTo(16,12); path.lineTo(9,19); canvas.drawPath(path,p); }
                case "play" -> { path.moveTo(8,5); path.lineTo(19,12); path.lineTo(8,19); path.close(); canvas.drawPath(path,p); }
                default -> { canvas.drawLine(8,6,8,18,p); canvas.drawLine(16,6,16,18,p); }
            }
            canvas.restore();
        }
    }
    final class Ring extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float fraction; final RectF oval = new RectF();
        Ring() { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void setFraction(float f) { fraction = Math.max(0,Math.min(1,f)); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(7)); p.setStrokeCap(Paint.Cap.ROUND);
            oval.set(dp(8),dp(8),getWidth()-dp(8),getHeight()-dp(8));
            p.setColor(0x558FC997); canvas.drawOval(oval,p); p.setColor(0xFFDCEFD0);
            canvas.drawArc(oval,-90,360*fraction,false,p);
            p.setStrokeWidth(dp(2)); float mid=getWidth()/2f, y=getHeight()/2f;
            canvas.drawLine(mid-dp(4),y-dp(7),mid-dp(4),y+dp(7),p); canvas.drawLine(mid+dp(4),y-dp(7),mid+dp(4),y+dp(7),p);
        }
    }
    final class UsageBar extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float fraction;
        UsageBar() { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void setFraction(float f) { fraction=Math.max(0,Math.min(1,f)); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            p.setColor(line); canvas.drawRoundRect(0,0,getWidth(),getHeight(),getHeight()/2f,getHeight()/2f,p);
            p.setColor(dark ? 0xFF98CBA7 : accent); canvas.drawRoundRect(0,0,getWidth()*fraction,getHeight(),getHeight()/2f,getHeight()/2f,p);
        }
    }
    final class Chart extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); long[] values = new long[0]; String[] labels = new String[0]; int selected;
        Chart() { super(c); setFocusable(true); }
        void data(long[] values, String[] labels, int selected) {
            this.values=values; this.labels=labels; this.selected=selected;
            StringBuilder description=new StringBuilder("Tracked usage. ");
            for(int i=0;i<values.length;i++) description.append(labels[i]).append(": ").append(usage(values[i])).append(". ");
            setContentDescription(description); invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if(values.length==0)return;
            long max=60_000; for(long v:values)max=Math.max(max,v);
            float cell=getWidth()/(float)values.length, baseline=getHeight()-dp(32), height=baseline-dp(12);
            for(int i=0;i<values.length;i++) {
                float bar=Math.max(values[i]>0?dp(4):dp(2),height*values[i]/max), x=cell*i+cell*.26f;
                p.setColor(i==selected?(dark?0xFF9BCEA3:accent):(dark?0xFF4E7055:0xFFC7DFC0));
                canvas.drawRoundRect(x,baseline-bar,x+cell*.48f,baseline,dp(7),dp(7),p);
                p.setColor(muted); p.setTextSize(dp(12)*getResources().getConfiguration().fontScale); p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(labels[i],cell*(i+.5f),getHeight()-dp(6),p);
            }
        }
    }
}
