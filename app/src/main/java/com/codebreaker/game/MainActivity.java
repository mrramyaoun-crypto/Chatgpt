package com.codebreaker.game;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int PAPER=Color.rgb(218,184,111);
    static final int PAPER_LIGHT=Color.rgb(236,210,151);
    static final int PAPER_DARK=Color.rgb(151,105,54);
    static final int INK=Color.rgb(55,37,24);
    static final int FADED=Color.rgb(116,83,52);
    static final int WOOD=Color.rgb(55,34,23);
    static final int WOOD_DARK=Color.rgb(30,20,15);
    static final int BRASS=Color.rgb(173,130,61);
    static final int BRASS_LIGHT=Color.rgb(205,166,91);
    static final int IVORY=Color.rgb(231,216,177);
    static final int RED_INK=Color.rgb(116,42,30);

    static final int S_KEY=1, S_RETURN=2, S_SCRATCH=3, S_REJECT=4, S_SUCCESS=5;

    final Random rnd=new Random();
    final ArrayList<String> ALL=new ArrayList<>();
    final ExecutorService puzzlePool=Executors.newSingleThreadExecutor();
    final ExecutorService soundPool=Executors.newFixedThreadPool(4);

    final HashSet<Character> eliminated=new HashSet<>();
    final ArrayList<String> candidates=new ArrayList<>();
    final HashSet<String> crossedCandidates=new HashSet<>();
    final ArrayList<TextView> clueCodeViews=new ArrayList<>();
    final ArrayList<Clue> visibleClues=new ArrayList<>();
    final Button[] digitKeys=new Button[10];

    ScrollView appScroll, candidateScroll;
    LinearLayout root, cluesBox, candidateBox, workingSlots, machine;
    Button newCipherBtn, difficultyBtn, assistBtn, soundBtn, resetBtn, decipherBtn;
    TextView resultStamp;
    FrameLayout answerStage;
    final char[] answerSlots=new char[]{'\0','\0','\0','\0'};
    int activeSlot=0;

    String mode="normal";
    Puzzle puzzle;
    boolean attempted=false, assistUsed=false, soundOn=true, solved=false;

    static class Score {
        int total,right;
        Score(int total,int right){this.total=total;this.right=right;}
    }

    static class Clue {
        String code;
        int total,right;
        Clue(String code,int total,int right){this.code=code;this.total=total;this.right=right;}
        String shortText(){
            if(total==1&&right==1)return "1 CORRECT · RIGHT PLACE";
            if(total==1&&right==0)return "1 CORRECT · WRONG PLACE";
            if(total==2&&right==0)return "2 CORRECT · BOTH MISPLACED";
            return "2 CORRECT · 1 RIGHT / 1 WRONG";
        }
    }

    static class Puzzle {
        String secret;
        ArrayList<Clue> clues;
        Puzzle(String secret,ArrayList<Clue> clues){this.secret=secret;this.clues=clues;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(WOOD_DARK);
        getWindow().setNavigationBarColor(WOOD_DARK);
        if(Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        genCodes("",new boolean[10]);
        buildUi();
        startPuzzle();
    }

    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    int statusBarHeight(){
        int id=getResources().getIdentifier("status_bar_height","dimen","android");
        return id>0?getResources().getDimensionPixelSize(id):dp(24);
    }

    GradientDrawable rounded(int fill,int stroke,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if(stroke!=Color.TRANSPARENT)g.setStroke(dp(1),stroke);
        return g;
    }

    GradientDrawable splitShape(int fill,int stroke,boolean left){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1),stroke);
        float r=dp(11);
        if(left)g.setCornerRadii(new float[]{r,r,0,0,0,0,r,r});
        else g.setCornerRadii(new float[]{0,0,r,r,r,r,0,0});
        return g;
    }

    StateListDrawable keyBackground(boolean eliminatedKey){
        StateListDrawable state=new StateListDrawable();

        GradientDrawable pressed=new GradientDrawable();
        pressed.setColor(eliminatedKey?Color.rgb(74,57,45):Color.rgb(188,158,103));
        pressed.setCornerRadius(dp(7));
        pressed.setStroke(dp(3),Color.rgb(27,19,14));

        GradientDrawable normal=new GradientDrawable();
        normal.setColor(eliminatedKey?Color.rgb(101,79,61):IVORY);
        normal.setCornerRadius(dp(7));
        normal.setStroke(dp(3),eliminatedKey?Color.rgb(50,34,26):Color.rgb(54,39,29));

        state.addState(new int[]{android.R.attr.state_pressed},pressed);
        state.addState(new int[]{},normal);
        return state;
    }

    TextView typeText(String text,int sp,boolean bold){
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(INK);
        v.setTypeface(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL);
        return v;
    }

    Button oldButton(String text){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(12);
        b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        b.setTextColor(Color.rgb(248,231,190));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(7),0,dp(7),0);
        b.setBackground(rounded(Color.rgb(91,57,35),BRASS,10));
        if(Build.VERSION.SDK_INT>=21)b.setElevation(dp(2));
        return b;
    }

    class AgedPaperDrawable extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final float radius;
        final boolean card;
        AgedPaperDrawable(float radiusDp,boolean card){radius=dp(radiusDp);this.card=card;}
        @Override public void draw(Canvas c){
            RectF r=new RectF(getBounds());
            p.setStyle(Paint.Style.FILL);
            p.setColor(card?Color.rgb(229,198,133):PAPER);
            c.drawRoundRect(r,radius,radius,p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(card?1.2f:2f));
            p.setColor(card?Color.rgb(150,107,61):Color.rgb(105,70,39));
            c.drawRoundRect(new RectF(r.left+1,r.top+1,r.right-1,r.bottom-1),radius,radius,p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(card?32:26,72,43,20));
            int w=Math.max(1,getBounds().width()),h=Math.max(1,getBounds().height());
            for(int i=0;i<(card?18:55);i++){
                float x=(i*73+29)%w;
                float y=(i*41+17)%h;
                float rr=(i%3==0)?1.4f:0.8f;
                c.drawCircle(r.left+x,r.top+y,dp(rr),p);
            }

            p.setColor(Color.argb(card?34:45,77,39,16));
            c.drawRect(r.left,r.top,r.right,r.top+dp(card?2:4),p);
            c.drawRect(r.left,r.bottom-dp(card?2:4),r.right,r.bottom,p);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter cf){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    class WoodDrawable extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c){
            RectF r=new RectF(getBounds());
            p.setStyle(Paint.Style.FILL);
            p.setColor(WOOD);
            c.drawRoundRect(r,dp(16),dp(16),p);
            p.setColor(Color.argb(24,235,187,105));
            for(int i=0;i<28;i++){
                float x=r.left+((i*53+19)%Math.max(1,(int)r.width()));
                float y=r.top+((i*37+11)%Math.max(1,(int)r.height()));
                c.drawCircle(x,y,dp(.65f),p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(BRASS);
            c.drawRoundRect(new RectF(r.left+1,r.top+1,r.right-1,r.bottom-1),dp(16),dp(16),p);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter cf){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    void genCodes(String p,boolean[] used){
        if(p.length()==4){ALL.add(p);return;}
        for(int i=0;i<10;i++)if(!used[i]){
            used[i]=true;
            genCodes(p+i,used);
            used[i]=false;
        }
    }

    void buildUi(){
        appScroll=new ScrollView(this);
        appScroll.setFillViewport(true);
        appScroll.setClipToPadding(false);
        appScroll.setBackgroundColor(WOOD_DARK);

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8),statusBarHeight()+dp(6),dp(8),dp(24));
        appScroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        setContentView(appScroll);

        LinearLayout paperPane=new LinearLayout(this);
        paperPane.setOrientation(LinearLayout.VERTICAL);
        paperPane.setPadding(dp(7),dp(7),dp(7),dp(5));
        paperPane.setBackground(new AgedPaperDrawable(14,false));
        root.addView(paperPane,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout split=new LinearLayout(this);
        split.setGravity(Gravity.CENTER);
        newCipherBtn=oldButton("NEW CIPHER");
        newCipherBtn.setBackground(splitShape(Color.rgb(92,57,34),BRASS,true));
        difficultyBtn=oldButton("+");
        difficultyBtn.setTextSize(20);
        difficultyBtn.setBackground(splitShape(Color.rgb(92,57,34),BRASS,false));
        split.addView(newCipherBtn,new LinearLayout.LayoutParams(0,dp(38),1));
        split.addView(difficultyBtn,new LinearLayout.LayoutParams(dp(48),dp(38)));
        paperPane.addView(split,new LinearLayout.LayoutParams(-1,-2));
        newCipherBtn.setOnClickListener(v->{pressAnim(v);playSound(S_RETURN);startPuzzle();});
        difficultyBtn.setOnClickListener(v->{pressAnim(v);playSound(S_KEY);showDifficultyPopup();});

        cluesBox=new LinearLayout(this);
        cluesBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,dp(6),0,0);
        paperPane.addView(cluesBox,cp);

        machine=new LinearLayout(this);
        machine.setOrientation(LinearLayout.VERTICAL);
        machine.setPadding(dp(8),dp(6),dp(8),dp(8));
        machine.setBackground(new WoodDrawable());
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);
        mp.setMargins(0,dp(6),0,0);
        root.addView(machine,mp);

        LinearLayout utilityRow=new LinearLayout(this);
        utilityRow.setGravity(Gravity.CENTER_VERTICAL);
        Space spacer=new Space(this);
        utilityRow.addView(spacer,new LinearLayout.LayoutParams(0,dp(30),1));

        assistBtn=miniUtility("HINT");
        assistBtn.setTextSize(8);
        resetBtn=miniUtility("↺");
        soundBtn=miniUtility("♪");
        utilityRow.addView(assistBtn,new LinearLayout.LayoutParams(dp(42),dp(32)));
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(dp(42),dp(32));
        ulp.setMargins(dp(5),0,0,0);
        utilityRow.addView(resetBtn,ulp);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(dp(42),dp(32));
        slp.setMargins(dp(5),0,0,0);
        utilityRow.addView(soundBtn,slp);
        machine.addView(utilityRow,new LinearLayout.LayoutParams(-1,dp(34)));

        assistBtn.setOnClickListener(v->{pressAnim(v);useAssist();});
        resetBtn.setOnClickListener(v->{pressAnim(v);playSound(S_RETURN);resetBoard();});
        soundBtn.setOnClickListener(v->{
            soundOn=!soundOn;
            soundBtn.setText(soundOn?"♪":"×");
            if(soundOn)playSound(S_KEY);
        });

        answerStage=new FrameLayout(this);
        workingSlots=new LinearLayout(this);
        workingSlots.setGravity(Gravity.CENTER);
        answerStage.addView(workingSlots,new FrameLayout.LayoutParams(-1,dp(50),Gravity.CENTER));

        resultStamp=typeText("",12,true);
        resultStamp.setGravity(Gravity.CENTER);
        resultStamp.setTextColor(RED_INK);
        resultStamp.setVisibility(View.GONE);
        FrameLayout.LayoutParams rsp=new FrameLayout.LayoutParams(-1,dp(40),Gravity.CENTER);
        rsp.setMargins(dp(14),0,dp(14),0);
        answerStage.addView(resultStamp,rsp);

        LinearLayout.LayoutParams asp=new LinearLayout.LayoutParams(-1,dp(52));
        asp.setMargins(0,dp(1),0,dp(2));
        machine.addView(answerStage,asp);

        decipherBtn=oldButton("DECIPHER");
        decipherBtn.setTextSize(13);
        decipherBtn.setTextColor(Color.rgb(45,27,18));
        decipherBtn.setBackground(rounded(BRASS_LIGHT,Color.rgb(75,47,25),8));
        LinearLayout.LayoutParams dcp=new LinearLayout.LayoutParams(-1,dp(39));
        dcp.setMargins(dp(2),0,dp(2),dp(5));
        machine.addView(decipherBtn,dcp);
        decipherBtn.setOnClickListener(v->{pressAnim(v);checkWorking();});

        candidateScroll=new ScrollView(this);
        candidateScroll.setFillViewport(false);
        candidateScroll.setNestedScrollingEnabled(true);
        candidateScroll.setBackground(new AgedPaperDrawable(8,true));
        candidateBox=new LinearLayout(this);
        candidateBox.setOrientation(LinearLayout.VERTICAL);
        candidateBox.setPadding(dp(5),dp(3),dp(5),dp(3));
        candidateScroll.addView(candidateBox,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams csp=new LinearLayout.LayoutParams(-1,dp(56));
        csp.setMargins(0,0,0,dp(5));
        machine.addView(candidateScroll,csp);

        addKeypadRow(new String[]{"1","2","3"});
        addKeypadRow(new String[]{"4","5","6"});
        addKeypadRow(new String[]{"7","8","9"});
        addActionRow();

        renderWorking();
        renderCandidates();
    }
    Button miniUtility(String text){
        Button b=oldButton(text);
        b.setTextSize(16);
        b.setPadding(0,0,0,0);
        b.setBackground(rounded(Color.rgb(75,48,33),BRASS,9));
        return b;
    }

    void addKeypadRow(String[] labels){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for(String label:labels){
            int d=Integer.parseInt(label);
            Button key=numberKey(d);
            digitKeys[d]=key;
            LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(dp(82),dp(43));
            kp.setMargins(dp(5),dp(2),dp(5),dp(2));
            row.addView(key,kp);
        }
        machine.addView(row,new LinearLayout.LayoutParams(-1,dp(47)));
    }
    void addActionRow(){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        Button back=mechanicalActionKey("⌫");
        back.setTextSize(18);
        back.setOnClickListener(v->{pressAnim(v);playSound(S_KEY);backspace();});

        Button zero=numberKey(0);
        digitKeys[0]=zero;

        Button ret=mechanicalActionKey("RETURN ↵");
        ret.setTextSize(9);
        ret.setOnClickListener(v->{pressAnim(v);saveCandidate();});

        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(dp(82),dp(43));
        p1.setMargins(dp(5),dp(2),dp(5),dp(2));
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(dp(82),dp(43));
        p2.setMargins(dp(5),dp(2),dp(5),dp(2));
        LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(dp(82),dp(43));
        p3.setMargins(dp(5),dp(2),dp(5),dp(2));
        row.addView(back,p1);
        row.addView(zero,p2);
        row.addView(ret,p3);
        machine.addView(row,new LinearLayout.LayoutParams(-1,dp(47)));
    }

    Button mechanicalActionKey(String text){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        b.setTextColor(INK);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2),0,dp(2),0);
        b.setBackground(keyBackground(false));
        if(Build.VERSION.SDK_INT>=21)b.setElevation(dp(4));
        return b;
    }
    Button numberKey(int digit){
        final char d=(char)('0'+digit);
        Button key=new Button(this);
        key.setText(String.valueOf(d));
        key.setTextSize(18);
        key.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        key.setTextColor(INK);
        key.setAllCaps(false);
        key.setMinHeight(0);
        key.setMinWidth(0);
        key.setPadding(0,0,0,0);
        key.setBackground(keyBackground(eliminated.contains(d)));
        if(Build.VERSION.SDK_INT>=21)key.setElevation(dp(4));
        key.setOnClickListener(v->{
            pressAnim(v);
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            playSound(S_KEY);
            answerSlots[activeSlot]=d;
            advanceActiveSlot();
            renderWorking();
        });
        key.setOnLongClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            toggleEliminated(d);
            return true;
        });
        return key;
    }

    void advanceActiveSlot(){
        for(int i=activeSlot+1;i<4;i++){
            if(answerSlots[i]=='\0'){activeSlot=i;return;}
        }
        for(int i=0;i<activeSlot;i++){
            if(answerSlots[i]=='\0'){activeSlot=i;return;}
        }
    }
    void pressAnim(View v){
        v.animate().scaleX(.93f).scaleY(.93f).setDuration(45).withEndAction(
                ()->v.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
        ).start();
    }

    void showDifficultyPopup(){
        LinearLayout menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(7),dp(7),dp(7),dp(7));
        menu.setBackground(new AgedPaperDrawable(10,true));

        final PopupWindow popup=new PopupWindow(menu,dp(168),-2,true);
        String[] modes={"EASY","NORMAL","HARD"};
        for(String m:modes){
            Button b=oldButton((mode.equals(m.toLowerCase())?"• ":"")+m);
            b.setTextColor(INK);
            b.setBackground(rounded(Color.rgb(221,188,122),PAPER_DARK,8));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(38));
            p.setMargins(0,dp(2),0,dp(2));
            menu.addView(b,p);
            b.setOnClickListener(v->{
                mode=m.toLowerCase();
                popup.dismiss();
                playSound(S_RETURN);
                startPuzzle();
            });
        }
        popup.setBackgroundDrawable(rounded(PAPER,PAPER_DARK,10));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popup.showAsDropDown(difficultyBtn,-dp(120),dp(3));
    }

    SpannableString styledDigits(String value,boolean wholeCross){
        SpannableString s=new SpannableString(value);
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(eliminated.contains(c)){
                s.setSpan(new StrikethroughSpan(),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new ForegroundColorSpan(Color.rgb(126,82,58)),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if(wholeCross&&value.length()>0){
            s.setSpan(new StrikethroughSpan(),0,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(Color.rgb(122,74,54)),0,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    void renderWorking(){
        workingSlots.removeAllViews();
        for(int i=0;i<4;i++){
            final int slotIndex=i;
            TextView slot=typeText("·",27,true);
            slot.setGravity(Gravity.CENTER);
            slot.setTextColor(i==activeSlot?BRASS_LIGHT:Color.rgb(230,205,151));
            slot.setBackgroundColor(Color.TRANSPARENT);
            if(answerSlots[i]!='\0'){
                slot.setText(styledDigits(String.valueOf(answerSlots[i]),false));
                if(i==activeSlot)slot.setTextColor(BRASS_LIGHT);
            }
            slot.setOnClickListener(v->{
                activeSlot=slotIndex;
                playSound(S_KEY);
                renderWorking();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(58),dp(46));
            lp.setMargins(dp(4),0,dp(4),0);
            workingSlots.addView(slot,lp);
        }
    }
    void renderCandidates(){
        candidateBox.removeAllViews();
        if(candidates.isEmpty()){
            TextView empty=typeText("·   ·   ·   ·",13,false);
            empty.setTextColor(Color.rgb(146,107,67));
            empty.setGravity(Gravity.CENTER);
            candidateBox.addView(empty,new LinearLayout.LayoutParams(-1,dp(32)));
            return;
        }

        for(int i=0;i<candidates.size();i+=4){
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for(int j=0;j<4;j++){
                int idx=i+j;
                if(idx<candidates.size()){
                    final String value=candidates.get(idx);
                    boolean crossed=crossedCandidates.contains(value);
                    TextView item=typeText("",13,true);
                    item.setGravity(Gravity.CENTER);
                    item.setText(styledDigits(value+",",crossed));
                    item.setAlpha(crossed?.28f:1f);
                    item.setBackgroundColor(Color.TRANSPARENT);
                    item.setOnClickListener(v->{
                        playSound(S_SCRATCH);
                        if(crossedCandidates.contains(value))crossedCandidates.remove(value);
                        else crossedCandidates.add(value);
                        renderCandidates();
                    });
                    item.setOnLongClickListener(v->{
                        playSound(S_RETURN);
                        candidates.remove(value);
                        crossedCandidates.remove(value);
                        renderCandidates();
                        return true;
                    });
                    row.addView(item,new LinearLayout.LayoutParams(0,dp(30),1));
                }else{
                    Space space=new Space(this);
                    row.addView(space,new LinearLayout.LayoutParams(0,dp(30),1));
                }
            }
            candidateBox.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    void saveCandidate(){
        String value=currentPartial();
        if(value.length()==0){
            playSound(S_REJECT);
            flashStamp("NO CIPHER",false);
            return;
        }
        if(!candidates.contains(value)){
            candidates.add(value);
            Collections.sort(candidates,new Comparator<String>(){
                @Override public int compare(String a,String b){
                    int na=Integer.parseInt(a),nb=Integer.parseInt(b);
                    if(na!=nb)return Integer.compare(na,nb);
                    if(a.length()!=b.length())return Integer.compare(a.length(),b.length());
                    return a.compareTo(b);
                }
            });
        }
        clearAnswerSlots();
        renderWorking();
        renderCandidates();
        candidateScroll.post(()->candidateScroll.fullScroll(View.FOCUS_DOWN));
        playSound(S_RETURN);
    }

    String currentPartial(){
        StringBuilder b=new StringBuilder();
        for(char c:answerSlots)if(c!='\0')b.append(c);
        return b.toString();
    }

    String currentGuess(){
        for(char c:answerSlots)if(c=='\0')return null;
        return new String(answerSlots);
    }

    void clearAnswerSlots(){
        Arrays.fill(answerSlots,'\0');
        activeSlot=0;
    }

    void backspace(){
        if(answerSlots[activeSlot]!='\0'){
            answerSlots[activeSlot]='\0';
        }else{
            for(int i=activeSlot-1;i>=0;i--){
                if(answerSlots[i]!='\0'){
                    answerSlots[i]='\0';
                    activeSlot=i;
                    break;
                }
            }
        }
        renderWorking();
    }
    void toggleEliminated(char d){
        if(eliminated.contains(d))eliminated.remove(d);
        else eliminated.add(d);
        playSound(S_SCRATCH);
        refreshAllMarks();
    }

    void refreshAllMarks(){
        for(int d=0;d<=9;d++){
            Button k=digitKeys[d];
            if(k==null)continue;
            char c=(char)('0'+d);
            boolean off=eliminated.contains(c);
            k.setBackground(keyBackground(off));
            k.setTextColor(off?Color.rgb(190,154,118):INK);
            k.setText(styledDigits(String.valueOf(c),false));
        }
        for(int i=0;i<clueCodeViews.size()&&i<visibleClues.size();i++){
            clueCodeViews.get(i).setText(styledDigits(visibleClues.get(i).code,false));
        }
        renderWorking();
        renderCandidates();
    }

    void renderPuzzle(){
        cluesBox.removeAllViews();
        clueCodeViews.clear();
        visibleClues.clear();
        String[] stamps={"①","②","③","④","⑤"};
        for(int i=0;i<puzzle.clues.size();i++){
            Clue c=puzzle.clues.get(i);
            visibleClues.add(c);

            LinearLayout card=new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(7),dp(4),dp(8),dp(4));
            card.setBackground(new AgedPaperDrawable(9,true));

            TextView stamp=typeText(stamps[i],14,true);
            stamp.setGravity(Gravity.CENTER);
            stamp.setTextColor(PAPER_DARK);
            card.addView(stamp,new LinearLayout.LayoutParams(dp(31),dp(38)));

            TextView code=typeText("",21,true);
            code.setGravity(Gravity.CENTER_VERTICAL);
            code.setText(styledDigits(c.code,false));
            clueCodeViews.add(code);
            card.addView(code,new LinearLayout.LayoutParams(dp(92),dp(40)));

            TextView rule=typeText(c.shortText(),10,true);
            rule.setTextColor(FADED);
            rule.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(rule,new LinearLayout.LayoutParams(0,dp(40),1));

            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));
            lp.setMargins(0,0,0,dp(3));
            cluesBox.addView(card,lp);
        }
    }

    void resetBoard(){
        clearAnswerSlots();
        eliminated.clear();
        candidates.clear();
        crossedCandidates.clear();
        solved=false;
        attempted=false;
        hideStamp();
        refreshAllMarks();
    }

    void startPuzzle(){
        newCipherBtn.setEnabled(false);
        newCipherBtn.setText("...");
        difficultyBtn.setEnabled(false);
        assistBtn.setEnabled(false);
        decipherBtn.setEnabled(false);
        clearAnswerSlots();
        eliminated.clear();
        candidates.clear();
        crossedCandidates.clear();
        attempted=false;
        assistUsed=false;
        solved=false;
        hideStamp();
        refreshAllMarks();

        final String requestedMode=mode;
        puzzlePool.submit(()->{
            Puzzle p=generate(requestedMode);
            runOnUiThread(()->{
                puzzle=p;
                renderPuzzle();
                renderWorking();
                renderCandidates();
                refreshAllMarks();
                newCipherBtn.setText("NEW CIPHER");
                newCipherBtn.setEnabled(true);
                difficultyBtn.setEnabled(true);
                assistBtn.setEnabled(true);
                assistBtn.setText("HINT");
                decipherBtn.setEnabled(true);
                appScroll.post(()->appScroll.smoothScrollTo(0,0));
            });
        });
    }

    void useAssist(){
        if(puzzle==null||assistUsed)return;
        if(mode.equals("hard")&&!attempted){
            playSound(S_REJECT);
            flashStamp("DECIPHER ONCE FIRST",false);
            return;
        }
        ArrayList<Character> falseDigits=new ArrayList<>();
        for(char d='0';d<='9';d++){
            if(puzzle.secret.indexOf(d)<0&&!eliminated.contains(d))falseDigits.add(d);
        }
        Collections.shuffle(falseDigits,rnd);
        int n=mode.equals("easy")?Math.min(2,falseDigits.size()):Math.min(1,falseDigits.size());
        for(int i=0;i<n;i++)eliminated.add(falseDigits.get(i));
        assistUsed=true;
        assistBtn.setText("HINT");
        assistBtn.setEnabled(false);
        playSound(S_SCRATCH);
        refreshAllMarks();
    }

    void checkWorking(){
        if(puzzle==null||solved)return;

        String guess=currentGuess();
        if(guess==null){
            playSound(S_REJECT);
            flashStamp("4 DIGITS REQUIRED",false);
            return;
        }

        HashSet<Character> unique=new HashSet<>();
        for(char c:answerSlots)unique.add(c);
        if(unique.size()!=4){
            playSound(S_REJECT);
            flashStamp("NO REPEATS",false);
            return;
        }

        attempted=true;
        if(guess.equals(puzzle.secret)){
            solved=true;
            decipherBtn.setEnabled(false);
            flashStamp("CIPHER BROKEN",true);
            playSound(S_SUCCESS);
        }else{
            playSound(S_REJECT);
            flashStamp("CIPHER REJECTED",false);
        }
    }
    void flashStamp(String text,boolean stay){
        resultStamp.removeCallbacks(hideStampRunnable);
        resultStamp.animate().cancel();
        resultStamp.setText(text);
        resultStamp.setVisibility(View.VISIBLE);
        resultStamp.setAlpha(0f);
        resultStamp.animate().alpha(1f).setDuration(120).start();

        if(!stay)resultStamp.postDelayed(hideStampRunnable,1100);
    }

    final Runnable hideStampRunnable=()->{
        if(resultStamp==null)return;
        resultStamp.animate().cancel();
        resultStamp.animate().alpha(0f).setDuration(400).withEndAction(()->{
            resultStamp.setVisibility(View.GONE);
            resultStamp.setText("");
            resultStamp.setAlpha(1f);
        }).start();
    };

    void hideStamp(){
        if(resultStamp!=null){
            resultStamp.removeCallbacks(hideStampRunnable);
            resultStamp.animate().cancel();
            resultStamp.setVisibility(View.GONE);
            resultStamp.setText("");
            resultStamp.setAlpha(1f);
        }
    }
    Score score(String secret,String clue){
        int total=0,right=0;
        for(int i=0;i<4;i++){
            if(secret.charAt(i)==clue.charAt(i))right++;
            if(clue.indexOf(secret.charAt(i))>=0)total++;
        }
        return new Score(total,right);
    }

    boolean matches(String candidate,Clue c){
        Score s=score(candidate,c.code);
        return s.total==c.total&&s.right==c.right;
    }

    ArrayList<String> filter(List<String> in,List<Clue> clues){
        ArrayList<String> out=new ArrayList<>();
        outer:for(String s:in){
            for(Clue c:clues)if(!matches(s,c))continue outer;
            out.add(s);
        }
        return out;
    }

    boolean disjoint(String a,String b){
        for(int i=0;i<4;i++)if(b.indexOf(a.charAt(i))>=0)return false;
        return true;
    }

    char fixedDigit(String secret,Clue c){
        for(int i=0;i<4;i++)if(secret.charAt(i)==c.code.charAt(i))return secret.charAt(i);
        return '?';
    }

    boolean containsExactlyOne(String code,char a,char b){
        int n=0;
        if(code.indexOf(a)>=0)n++;
        if(code.indexOf(b)>=0)n++;
        return n==1;
    }

    ArrayList<Clue> poolFor(String secret,int total,int right){
        ArrayList<Clue> p=new ArrayList<>();
        for(String code:ALL){
            Score s=score(secret,code);
            if(s.total==total&&s.right==right)p.add(new Clue(code,total,right));
        }
        Collections.shuffle(p,rnd);
        return p;
    }

    Clue chooseByCount(ArrayList<Clue> pool,List<String> candidates,int min,int max,Set<String> used){
        for(Clue c:pool){
            if(used.contains(c.code))continue;
            int n=0;
            for(String s:candidates)if(matches(s,c))n++;
            if(n>=min&&n<=max)return c;
        }
        return null;
    }

    Puzzle generate(String m){
        while(true){
            String secret=ALL.get(rnd.nextInt(ALL.size()));
            ArrayList<Clue> fixed=poolFor(secret,1,1);
            if(fixed.size()<2)continue;

            Clue c1=fixed.get(rnd.nextInt(fixed.size())),c2=null;
            for(Clue x:fixed)if(disjoint(c1.code,x.code)){c2=x;break;}
            if(c2==null)continue;

            char d1=fixedDigit(secret,c1),d2=fixedDigit(secret,c2);
            ArrayList<Clue> clues=new ArrayList<>(Arrays.asList(c1,c2));
            ArrayList<String> cand=filter(ALL,clues);
            Set<String> used=new HashSet<>();
            used.add(c1.code);
            used.add(c2.code);

            int h3t=m.equals("easy")?2:1;
            int h3r=m.equals("easy")?1:0;
            int h4t=2;
            int h4r=m.equals("hard")?1:0;

            int h3min=m.equals("easy")?2:(m.equals("hard")?10:6);
            int h3max=m.equals("easy")?6:(m.equals("hard")?18:12);
            int h4min=m.equals("hard")?3:2;
            int h4max=m.equals("easy")?3:(m.equals("hard")?6:4);

            ArrayList<Clue> p3=poolFor(secret,h3t,h3r);
            ArrayList<Clue> p4=poolFor(secret,h4t,h4r);
            ArrayList<Clue> p5=poolFor(secret,2,0);

            Clue c3=null;
            for(Clue x:p3){
                if(!containsExactlyOne(x.code,d1,d2))continue;
                int n=0;
                for(String z:cand)if(matches(z,x))n++;
                if(n>=h3min&&n<=h3max){c3=x;break;}
            }
            if(c3==null)continue;
            clues.add(c3);
            used.add(c3.code);
            cand=filter(cand,Collections.singletonList(c3));

            Clue c4=chooseByCount(p4,cand,h4min,h4max,used);
            if(c4==null)continue;
            clues.add(c4);
            used.add(c4.code);
            cand=filter(cand,Collections.singletonList(c4));

            Clue c5=chooseByCount(p5,cand,1,1,used);
            if(c5==null)continue;
            clues.add(c5);
            cand=filter(cand,Collections.singletonList(c5));

            if(cand.size()!=1||!cand.get(0).equals(secret))continue;

            Score s1=score(secret,c1.code),s2=score(secret,c2.code),s3=score(secret,c3.code),
                    s4=score(secret,c4.code),s5=score(secret,c5.code);
            boolean pattern=s1.total==1&&s1.right==1
                    &&s2.total==1&&s2.right==1
                    &&disjoint(c1.code,c2.code)
                    &&s3.total==h3t&&s3.right==h3r
                    &&containsExactlyOne(c3.code,d1,d2)
                    &&s4.total==2&&s4.right==h4r
                    &&s5.total==2&&s5.right==0;
            if(pattern)return new Puzzle(secret,clues);
        }
    }

    void playSound(int type){
        if(!soundOn)return;
        soundPool.submit(()->{
            try{
                int sr=22050;
                int ms=(type==S_SUCCESS)?520:(type==S_RETURN?280:(type==S_REJECT?120:(type==S_SCRATCH?95:55)));
                int samples=sr*ms/1000;
                short[] pcm=new short[samples];
                Random r=new Random(System.nanoTime()+type*131L);
                double previousNoise=0;

                for(int i=0;i<samples;i++){
                    double t=i/(double)sr;
                    double noise=r.nextDouble()*2-1;
                    double click=noise-previousNoise;
                    previousNoise=noise;
                    double x=0;

                    if(type==S_KEY){
                        double e=Math.exp(-t*95);
                        x=click*.55*e
                                +Math.sin(2*Math.PI*2400*t)*.22*Math.exp(-t*80)
                                +Math.sin(2*Math.PI*3400*t)*.12*Math.exp(-t*110);
                    }else if(type==S_RETURN){
                        double e=Math.exp(-t*80);
                        x=click*.48*e+Math.sin(2*Math.PI*2200*t)*.16*e;
                        if(t>.075&&t<.17){
                            double u=t-.075;
                            x+=click*.16*Math.exp(-u*16);
                        }
                        if(t>.15){
                            double u=t-.15;
                            x+=Math.sin(2*Math.PI*1760*u)*.24*Math.exp(-u*13)
                                    +Math.sin(2*Math.PI*2340*u)*.12*Math.exp(-u*15);
                        }
                    }else if(type==S_SCRATCH){
                        x=click*.32*Math.exp(-t*28);
                    }else if(type==S_REJECT){
                        double e1=Math.exp(-t*90);
                        x=click*.42*e1+Math.sin(2*Math.PI*1900*t)*.13*e1;
                        if(t>.055){
                            double u=t-.055;
                            double e2=Math.exp(-u*95);
                            x+=(noise-previousNoise)*.34*e2+Math.sin(2*Math.PI*1500*u)*.12*e2;
                        }
                    }else if(type==S_SUCCESS){
                        x=click*.38*Math.exp(-t*85);
                        if(t>.09){
                            double u=t-.09;
                            x+=Math.sin(2*Math.PI*1760*u)*.26*Math.exp(-u*6.8)
                                    +Math.sin(2*Math.PI*2348*u)*.17*Math.exp(-u*7.3);
                        }
                        if(t>.255){
                            double u=t-.255;
                            x+=Math.sin(2*Math.PI*2093*u)*.22*Math.exp(-u*8)
                                    +Math.sin(2*Math.PI*2790*u)*.11*Math.exp(-u*9);
                        }
                    }

                    x=Math.max(-1,Math.min(1,x));
                    pcm[i]=(short)(x*14500);
                }

                AudioAttributes attrs=new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                AudioFormat fmt=new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                AudioTrack track=new AudioTrack(attrs,fmt,pcm.length*2,AudioTrack.MODE_STATIC,AudioManager.AUDIO_SESSION_ID_GENERATE);
                track.write(pcm,0,pcm.length);
                track.setVolume(type==S_SUCCESS?.72f:.50f);
                track.play();
                Thread.sleep(ms+30L);
                track.release();
            }catch(Exception ignored){}
        });
    }
    @Override protected void onDestroy(){
        puzzlePool.shutdownNow();
        soundPool.shutdownNow();
        super.onDestroy();
    }
}
