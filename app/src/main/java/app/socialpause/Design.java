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
    final int background, surface, ink, muted, line, mint, accent, onAccent, warning, warningInk;
    Design(Context c) {
        this.c = c; dark = Appearance.isDark(c);
        background = Color.parseColor(dark ? "#14251F" : "#F6F8F2");
        surface = Color.parseColor(dark ? "#223A31" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#E7F1E5" : "#1E3932");
        muted = Color.parseColor(dark ? "#B0C3B5" : "#66796E");
        line = Color.parseColor(dark ? "#3E5648" : "#DFE5DA");
        mint = Color.parseColor(dark ? "#314E35" : "#E0F0D3");
        accent = Color.parseColor(dark?"#98CBA7":"#195E4C");
        onAccent=dark?0xFF14251F:Color.WHITE;warning=dark?0xFF453C24:0xFFFFF0CF;warningInk=dark?0xFFF1D095:0xFF7D5720;
    }
    int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
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
        b.setTextColor(filled ? onAccent : ink); b.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        b.setBackground(shape(filled ? accent : surface, 28)); b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setOnClickListener(v -> action.run()); return b;
    }
    /** Native checkable control, using the exact sun/moon paths from the supplied export. */
    final class ThemeSwitch extends CompoundButton {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.drawable.Drawable sun, moon;
        ThemeSwitch() {
            super(c);setButtonDrawable((android.graphics.drawable.Drawable)null);setBackground(shape(line,24));
            setContentDescription(c.getString(R.string.dark_mode));setChecked(dark);setFocusable(true);setClickable(true);
            setMinimumWidth(dp(86));setMinimumHeight(dp(48));
            sun=c.getDrawable(R.drawable.ic_theme_sun).mutate();moon=c.getDrawable(R.drawable.ic_theme_moon).mutate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);float half=getWidth()/2f;
            paint.setColor(surface);float left=isChecked()?half:dp(4);
            canvas.drawRoundRect(left,dp(4),isChecked()?getWidth()-dp(4):half,getHeight()-dp(4),dp(20),dp(20),paint);
            drawGlyph(canvas,sun,half/2f,!isChecked());drawGlyph(canvas,moon,half+half/2f,isChecked());
        }
        private void drawGlyph(Canvas canvas,android.graphics.drawable.Drawable icon,float center,boolean active) {
            int size=dp(20),left=Math.round(center-size/2f),top=(getHeight()-size)/2;
            icon.setTint(active?accent:muted);icon.setBounds(left,top,left+size,top+size);icon.draw(canvas);
        }
        @Override public CharSequence getAccessibilityClassName(){return Switch.class.getName();}
    }
    static int appIcon(String pkg) {
        return switch(pkg){case "com.instagram.android"->R.drawable.ic_instagram;case "com.twitter.android"->R.drawable.ic_x;case "com.reddit.frontpage"->R.drawable.ic_reddit;default->R.drawable.ic_pause;};
    }
    View badge(String pkg) {
        FrameLayout frame=new FrameLayout(c);frame.setBackground(shape(appColor(pkg),12));
        ImageView glyph=new ImageView(c){@Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);int inset=Math.round(Math.min(w,h)*.23f);setPadding(inset,inset,inset,inset);}};glyph.setImageResource(appIcon(pkg));glyph.setImageTintList(android.content.res.ColorStateList.valueOf(dark?background:Color.WHITE));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);frame.addView(glyph,lp);
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return frame;
    }
    TextView pill(String label,boolean cooling) {
        TextView v=text(label,12,cooling?warningInk:ink,false);v.setBackground(shape(cooling?warning:mint,16));v.setPadding(dp(10),dp(5),dp(10),dp(5));return v;
    }
    int appColor(String pkg) {
        if(pkg.equals("com.instagram.android"))return dark?0xFFE48DB6:0xFFAD4379;
        if(pkg.equals("com.reddit.frontpage"))return dark?0xFFEEA087:0xFFC85B37;
        if(pkg.equals("com.twitter.android"))return dark?0xFFB2C5BB:0xFF233B32;
        int[] palette={0xFF397EB0,0xFF7761A8,0xFF328275,0xFFAC7032,0xFF8B617C};
        return palette[Math.floorMod(pkg.hashCode(),palette.length)];
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
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float fraction; int color=accent;
        void setColor(int color){this.color=color;invalidate();}
        UsageBar() { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void setFraction(float f) { fraction=Math.max(0,Math.min(1,f)); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            p.setColor(line); canvas.drawRoundRect(0,0,getWidth(),getHeight(),getHeight()/2f,getHeight()/2f,p);
            p.setColor(color); canvas.drawRoundRect(0,0,getWidth()*fraction,getHeight(),getHeight()/2f,getHeight()/2f,p);
        }
    }
    /** Stack colors match app badges. Day targets remain individually accessible. */
    final class Chart extends FrameLayout {
        private final DayBar[] bars=new DayBar[7];
        private final java.util.function.Consumer<java.time.LocalDate> listener;
        private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG);
        private long maximum=20*60_000;
        Chart(java.util.function.Consumer<java.time.LocalDate> listener) {
            super(c);this.listener=listener;setWillNotDraw(false);
            LinearLayout columns=row();LayoutParams lp=new LayoutParams(-1,-1);lp.leftMargin=dp(28);addView(columns,lp);
            for(int i=0;i<7;i++){bars[i]=new DayBar();columns.addView(bars[i],new LinearLayout.LayoutParams(0,-1,1));}
        }
        boolean containsDay(float x,float y) {
            Rect bounds=new Rect();for(DayBar bar:bars)if(bar.isEnabled()&&bar.getGlobalVisibleRect(bounds)&&bounds.contains((int)x,(int)y))return true;return false;
        }
        void data(java.util.List<java.util.Map<String,Long>> days,java.time.LocalDate monday,java.time.LocalDate selection){
            long max=1;for(var day:days){long sum=0;for(long value:day.values())sum+=value;max=Math.max(max,sum);}
            maximum=((max+20*60_000-1)/(20*60_000))*(20*60_000);
            for(int i=0;i<7;i++)bars[i].data(days.get(i),monday.plusDays(i),maximum,monday.plusDays(i).equals(selection));invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float base=getHeight()-dp(32),height=base-dp(12);
            grid.setTextAlign(Paint.Align.RIGHT);grid.setTextSize(dp(11));
            for(int tick=0;tick<=2;tick++){float y=base-height*tick/2;grid.setColor(line);canvas.drawLine(dp(28),y,getWidth(),y,grid);grid.setColor(muted);canvas.drawText(Long.toString(maximum/60_000*tick/2),dp(18),y+dp(4),grid);}
        }
        final class DayBar extends View {
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            java.util.Map<String,Long> values=java.util.Map.of();java.time.LocalDate day;long max=60_000,total;
            DayBar(){super(c);setFocusable(true);setClickable(true);setMinimumHeight(dp(48));setOnClickListener(v->{if(day!=null)listener.accept(day);});}
            void data(java.util.Map<String,Long> values,java.time.LocalDate day,long max,boolean selected){
                java.util.Map<String,Long> ordered=new java.util.LinkedHashMap<>();
                for(String pkg:java.util.List.of("com.instagram.android","com.twitter.android","com.reddit.frontpage"))if(values.containsKey(pkg))ordered.put(pkg,values.get(pkg));
                for(var entry:values.entrySet())ordered.putIfAbsent(entry.getKey(),entry.getValue());
                this.values=ordered;this.day=day;this.max=max;total=0;for(long v:values.values())total+=v;setSelected(selected);setEnabled(!day.isAfter(java.time.LocalDate.now()));
                StringBuilder description=new StringBuilder(day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM"))).append(": ").append(usage(total));
                for(var entry:values.entrySet())description.append(". ").append(AppController.label(c,entry.getKey())).append(" ").append(usage(entry.getValue()));
                setContentDescription(description+(selected?". Selected. Tap again for the week.":". Tap for this day."));invalidate();
            }
            @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());}
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);if(day==null)return;
                float left=getWidth()*.22f,right=getWidth()*.78f,base=getHeight()-dp(32),height=base-dp(12),y=base;
                if(isSelected()||isFocused()){
                    p.setStyle(Paint.Style.FILL);p.setColor(surface);canvas.drawRoundRect(dp(2),dp(2),getWidth()-dp(2),getHeight()-dp(2),dp(10),dp(10),p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(accent);canvas.drawRoundRect(dp(2),dp(2),getWidth()-dp(2),getHeight()-dp(2),dp(10),dp(10),p);p.setStyle(Paint.Style.FILL);
                }
                if(total==0){p.setColor(line);canvas.drawRoundRect(left,base-dp(2),right,base,dp(1),dp(1),p);}
                for(var entry:values.entrySet()){float h=height*entry.getValue()/max;p.setColor(appColor(entry.getKey()));canvas.drawRect(left,y-h,right,y,p);y-=h;}
                p.setColor(isEnabled()?ink:muted);p.setTextSize(dp(12)*Math.min(1.3f,getResources().getConfiguration().fontScale));p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.NARROW,Locale.getDefault()),getWidth()/2f,getHeight()-dp(9),p);
            }
        }
    }
}
