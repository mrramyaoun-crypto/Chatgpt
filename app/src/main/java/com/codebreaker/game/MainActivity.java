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
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int PAPER=Color.rgb(214,179,103);
    static final int PAPER_DARK=Color.rgb(137,93,48);
    static final int INK=Color.rgb(50,33,23);
    static final int FADED=Color.rgb(108,74,46);
    static final int WOOD=Color.rgb(52,32,22);
    static final int WOOD_DARK=Color.rgb(28,18,14);
    static final int BRASS=Color.rgb(164,120,53);
    static final int BRASS_LIGHT=Color.rgb(205,163,83);
    static final int IVORY=Color.rgb(229,211,166);
    static final int RED_INK=Color.rgb(113,42,31);

    final Random rnd=new Random();
    final ArrayList<String> ALL=new ArrayList<>();
    final ExecutorService puzzlePool=Executors.newSingleThreadExecutor();

    final HashSet<Character> eliminated=new HashSet<>();
    final ArrayList<String> candidates=new ArrayList<>();
    final HashSet<String> crossedCandidates=new HashSet<>();
    final ArrayList<TextView> clueCodeViews=new ArrayList<>();
    final ArrayList<Clue> visibleClues=new ArrayList<>();
    final Button[] digitKeys=new Button[10];

    LinearLayout root, cluesBox, workingSlots, machine;
    FlowLayout candidateBox;
    FrameLayout candidateWindow;
    Button newCipherBtn, difficultyBtn, assistBtn, resetBtn, resolveBtn, decipherBtn, deleteBtn, enterBtn;
    TextView resultStamp;
    FrameLayout answerStage;

    final char[] answerSlots=new char[]{'\0','\0','\0','\0'};
    int activeSlot=0;

    String mode="normal";
    Puzzle puzzle;
    boolean attempted=false, assistUsed=false, solved=false;

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

    class FlowLayout extends ViewGroup {
        FlowLayout(Context c){ super(c); }

        @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
            int width=MeasureSpec.getSize(widthMeasureSpec);
            int available=Math.max(0,width-getPaddingLeft()-getPaddingRight());
            int x=0, y=getPaddingTop(), lineH=0;

            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);
                measureChild(child,widthMeasureSpec,heightMeasureSpec);
                int cw=child.getMeasuredWidth();
                int ch=child.getMeasuredHeight();

                if(x>0 && x+cw>available){
                    y+=lineH;
                    x=0;
                    lineH=0;
                }
                x+=cw;
                lineH=Math.max(lineH,ch);
            }

            y+=lineH+getPaddingBottom();
            int desired=Math.max(getSuggestedMinimumHeight(),y);
            setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(desired,heightMeasureSpec));
        }

        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int available=Math.max(0,r-l-getPaddingLeft()-getPaddingRight());
            int x=0, y=getPaddingTop(), lineH=0;

            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);
                int cw=child.getMeasuredWidth();
                int ch=child.getMeasuredHeight();

                if(x>0 && x+cw>available){
                    y+=lineH;
                    x=0;
                    lineH=0;
                }

                int left=getPaddingLeft()+x;
                child.layout(left,y,left+cw,y+ch);
                x+=cw;
                lineH=Math.max(lineH,ch);
            }
        }
    }

    class ScratchTextView extends TextView {
        final Paint scratchPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean scratched=false;

        ScratchTextView(Context c){
            super(c);
            scratchPaint.setColor(Color.rgb(74,45,28));
            scratchPaint.setStrokeWidth(dp(2.1f));
            scratchPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setScratched(boolean value){
            scratched=value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(scratched){
                float left=getPaddingLeft();
                float right=getWidth()-getPaddingRight();
                float y=getHeight()*.50f;
                canvas.drawLine(left,y,right,y+dp(1),scratchPaint);
                canvas.drawLine(left,y+dp(3),right,y+dp(2),scratchPaint);
            }
        }
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

    Typeface oldFace(boolean bold){
        return Typeface.create("serif-monospace",bold?Typeface.BOLD:Typeface.NORMAL);
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
        float r=dp(10);
        if(left)g.setCornerRadii(new float[]{r,r,0,0,0,0,r,r});
        else g.setCornerRadii(new float[]{0,0,r,r,r,r,0,0});
        return g;
    }

    TextView typeText(String text,int sp,boolean bold){
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(INK);
        v.setTypeface(oldFace(bold));
        if(Build.VERSION.SDK_INT>=21)v.setLetterSpacing(.015f);
        return v;
    }

    Button oldButton(String text){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(11);
        b.setTypeface(oldFace(true));
        b.setTextColor(Color.rgb(246,224,177));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(6),0,dp(6),0);
        b.setBackground(rounded(Color.rgb(91,55,34),BRASS,8));
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
            p.setColor(card?Color.rgb(228,194,124):PAPER);
            c.drawRoundRect(r,radius,radius,p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(card?1.1f:2f));
            p.setColor(card?Color.rgb(143,99,53):Color.rgb(97,62,35));
            c.drawRoundRect(new RectF(r.left+1,r.top+1,r.right-1,r.bottom-1),radius,radius,p);

            int w=Math.max(1,getBounds().width());
            int h=Math.max(1,getBounds().height());

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(card?32:38,67,39,18));
            for(int i=0;i<(card?24:78);i++){
                float x=(i*71+31)%w;
                float y=(i*43+19)%h;
                float rr=(i%5==0)?1.5f:.7f;
                c.drawCircle(r.left+x,r.top+y,dp(rr),p);
            }

            p.setColor(Color.argb(card?29:42,91,49,21));
            for(int i=0;i<(card?6:13);i++){
                float x1=r.left+((i*89+14)%w);
                float y1=r.top+((i*51+9)%h);
                c.drawRect(x1,y1,Math.min(r.right,x1+dp(7+i%3)),Math.min(r.bottom,y1+dp(.8f)),p);
            }

            p.setColor(Color.argb(card?44:58,70,34,14));
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
            c.drawRoundRect(r,dp(15),dp(15),p);

            int w=Math.max(1,getBounds().width());
            int h=Math.max(1,getBounds().height());
            p.setColor(Color.argb(38,223,173,91));
            for(int i=0;i<52;i++){
                float x=r.left+((i*53+17)%w);
                float y=r.top+((i*37+13)%h);
                if(i%4==0)c.drawRect(x,y,Math.min(r.right,x+dp(8+i%7)),Math.min(r.bottom,y+dp(.8f)),p);
                else c.drawCircle(x,y,dp(.55f),p);
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(BRASS);
            c.drawRoundRect(new RectF(r.left+1,r.top+1,r.right-1,r.bottom-1),dp(15),dp(15),p);
        }

        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter cf){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    class MechanicalKeyDrawable extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final boolean eliminatedKey;
        final boolean pressed;

        MechanicalKeyDrawable(boolean eliminatedKey,boolean pressed){
            this.eliminatedKey=eliminatedKey;
            this.pressed=pressed;
        }

        @Override public void draw(Canvas c){
            RectF r=new RectF(getBounds());
            float faceInset=dp(pressed?5:3);

            p.setStyle(Paint.Style.FILL);
            p.setColor(pressed?Color.rgb(31,23,18):Color.rgb(40,28,22));
            c.drawRoundRect(r,dp(6),dp(6),p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(pressed?Color.rgb(105,72,38):BRASS);
            c.drawRoundRect(new RectF(r.left+1,r.top+1,r.right-1,r.bottom-1),dp(6),dp(6),p);

            RectF face=new RectF(r.left+faceInset,r.top+faceInset,r.right-faceInset,r.bottom-faceInset);
            p.setStyle(Paint.Style.FILL);
            p.setColor(eliminatedKey?Color.rgb(106,82,62):(pressed?Color.rgb(195,169,113):IVORY));
            c.drawRoundRect(face,dp(4),dp(4),p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(eliminatedKey?Color.rgb(65,43,32):Color.rgb(113,80,47));
            c.drawRoundRect(face,dp(4),dp(4),p);
        }

        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter cf){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    StateListDrawable keyBackground(boolean eliminatedKey){
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_pressed},new MechanicalKeyDrawable(eliminatedKey,true));
        state.addState(new int[]{},new MechanicalKeyDrawable(eliminatedKey,false));
        return state;
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
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WOOD_DARK);
        root.setPadding(dp(8),statusBarHeight()+dp(6),dp(8),dp(32));
        setContentView(root,new android.view.ViewGroup.LayoutParams(-1,-1));

        LinearLayout paperPane=new LinearLayout(this);
        paperPane.setOrientation(LinearLayout.VERTICAL);
        paperPane.setPadding(dp(7),dp(7),dp(7),dp(5));
        paperPane.setBackground(new AgedPaperDrawable(14,false));
        root.addView(paperPane,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout split=new LinearLayout(this);
        split.setGravity(Gravity.CENTER);

        newCipherBtn=oldButton("NEW CIPHER");
        assistBtn=oldButton("HINT");
        resetBtn=oldButton("RESET");
        resolveBtn=oldButton("RESOLVE");
        difficultyBtn=oldButton("+");

        newCipherBtn.setTextSize(9);
        assistBtn.setTextSize(9);
        resetBtn.setTextSize(9);
        resolveBtn.setTextSize(9);
        difficultyBtn.setTextSize(20);

        Button[] topActions={newCipherBtn,assistBtn,resetBtn,resolveBtn};
        for(Button b:topActions){
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(42),1f);
            bp.setMargins(dp(1),0,dp(1),0);
            split.addView(b,bp);
        }
        LinearLayout.LayoutParams plusLp=new LinearLayout.LayoutParams(dp(38),dp(42));
        plusLp.setMargins(dp(1),0,0,0);
        split.addView(difficultyBtn,plusLp);
        paperPane.addView(split,new LinearLayout.LayoutParams(-1,-2));

        newCipherBtn.setOnClickListener(v->{pressAnim(v);startPuzzle();});
        assistBtn.setOnClickListener(v->{pressAnim(v);useAssist();});
        resetBtn.setOnClickListener(v->{pressAnim(v);resetBoard();});
        resolveBtn.setOnClickListener(v->{pressAnim(v);revealSolution();});
        difficultyBtn.setOnClickListener(v->{pressAnim(v);showDifficultyPopup();});

        cluesBox=new LinearLayout(this);
        cluesBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,dp(6),0,0);
        paperPane.addView(cluesBox,cp);

        machine=new LinearLayout(this);
        machine.setOrientation(LinearLayout.VERTICAL);
        machine.setPadding(dp(8),dp(6),dp(8),dp(8));
        machine.setBackground(new WoodDrawable());
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,0,1f);
        mp.setMargins(0,dp(6),0,0);
        root.addView(machine,mp);

        answerStage=new FrameLayout(this);
        workingSlots=new LinearLayout(this);
        workingSlots.setGravity(Gravity.CENTER);
        answerStage.addView(workingSlots,new FrameLayout.LayoutParams(-1,dp(58),Gravity.CENTER));
        machine.addView(answerStage,new LinearLayout.LayoutParams(-1,dp(60)));

        FrameLayout messageArea=new FrameLayout(this);
        resultStamp=typeText("",13,true);
        resultStamp.setGravity(Gravity.CENTER);
        resultStamp.setTextColor(RED_INK);
        resultStamp.setPadding(dp(12),dp(3),dp(12),dp(3));
        resultStamp.setBackground(new AgedPaperDrawable(7,true));
        resultStamp.setVisibility(View.GONE);
        messageArea.addView(resultStamp,new FrameLayout.LayoutParams(-2,dp(34),Gravity.CENTER));
        machine.addView(messageArea,new LinearLayout.LayoutParams(-1,dp(35)));

        decipherBtn=oldButton("DECIPHER");
        decipherBtn.setTextSize(15);
        decipherBtn.setTextColor(INK);
        decipherBtn.setBackground(new MechanicalKeyDrawable(false,false));
        LinearLayout.LayoutParams dcp=new LinearLayout.LayoutParams(-1,dp(46));
        dcp.setMargins(dp(2),0,dp(2),dp(5));
        machine.addView(decipherBtn,dcp);
        decipherBtn.setOnClickListener(v->{pressAnim(v);checkWorking();});

        candidateWindow=new FrameLayout(this);
        candidateWindow.setBackground(new AgedPaperDrawable(8,true));
        candidateWindow.setClipChildren(true);

        candidateBox=new FlowLayout(this);
        candidateBox.setPadding(dp(10),dp(7),dp(10),dp(7));
        candidateBox.setMinimumHeight(dp(150));
        candidateWindow.addView(candidateBox,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.LEFT));

        LinearLayout.LayoutParams csp=new LinearLayout.LayoutParams(-1,0,1f);
        csp.setMargins(0,0,0,dp(8));
        machine.addView(candidateWindow,csp);

        addKeyboard();
        renderWorking();
        renderCandidates();
    }

    Button mechanicalKey(String text,int textSize){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(textSize);
        b.setTypeface(oldFace(true));
        b.setTextColor(INK);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2),0,dp(2),0);
        b.setBackground(keyBackground(false));
        if(Build.VERSION.SDK_INT>=21)b.setElevation(dp(3));
        return b;
    }

    void addKeyboard(){
        LinearLayout keyboard=new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams klp=new LinearLayout.LayoutParams(-1,dp(208));
        klp.setMargins(dp(2),0,dp(2),dp(6));
        machine.addView(keyboard,klp);

        addNumberRow(keyboard,new int[]{1,2,3});
        addNumberRow(keyboard,new int[]{4,5,6});
        addNumberRow(keyboard,new int[]{7,8,9});

        Button zero=numberKey(0);
        digitKeys[0]=zero;
        deleteBtn=mechanicalKey("DELETE",11);
        enterBtn=mechanicalKey("ENTER",12);
        addControlRow(keyboard,new Button[]{zero,deleteBtn,enterBtn});

        deleteBtn.setOnClickListener(v->{pressAnim(v);backspace();});
        enterBtn.setOnClickListener(v->{pressAnim(v);saveCandidate();});
    }

    void addControlRow(LinearLayout parent,Button[] buttons){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for(Button b:buttons){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);
            lp.setMargins(dp(4),dp(1),dp(4),dp(1));
            row.addView(b,lp);
        }
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    void addNumberRow(LinearLayout parent,int[] digits){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for(int d:digits){
            Button key=numberKey(d);
            digitKeys[d]=key;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);
            lp.setMargins(dp(4),dp(1),dp(4),dp(1));
            row.addView(key,lp);
        }
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    Button numberKey(int digit){
        final char d=(char)('0'+digit);
        Button key=mechanicalKey(String.valueOf(d),21);
        key.setBackground(keyBackground(eliminated.contains(d)));

        key.setOnClickListener(v->{
            pressAnim(v);
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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
        v.animate().scaleX(.95f).scaleY(.95f).setDuration(45).withEndAction(
                ()->v.animate().scaleX(1f).scaleY(1f).setDuration(65).start()
        ).start();
    }

    void showDifficultyPopup(){
        LinearLayout menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(7),dp(7),dp(7),dp(7));
        menu.setBackground(new AgedPaperDrawable(10,true));

        final PopupWindow popup=new PopupWindow(menu,dp(172),-2,true);
        String[] choices={"EASY","NORMAL","HARD"};

        for(String choice:choices){
            Button b=oldButton(choice);
            b.setTextColor(INK);
            b.setBackground(rounded(Color.rgb(221,188,122),PAPER_DARK,7));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(37));
            p.setMargins(0,dp(2),0,dp(2));
            menu.addView(b,p);

            b.setOnClickListener(v->{
                popup.dismiss();
                mode=choice.toLowerCase();
                startPuzzle();
            });
        }

        popup.setBackgroundDrawable(rounded(PAPER,PAPER_DARK,10));
        popup.setOutsideTouchable(true);
        if(Build.VERSION.SDK_INT>=21)popup.setElevation(dp(8));
        popup.showAsDropDown(difficultyBtn,-dp(124),dp(3));
    }

    void revealSolution(){
        if(puzzle==null)return;
        for(int i=0;i<4;i++)answerSlots[i]=puzzle.secret.charAt(i);
        activeSlot=0;
        renderWorking();
        flashStamp("CIPHER REVEALED",false);
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
        }
        return s;
    }

    void renderWorking(){
        workingSlots.removeAllViews();

        for(int i=0;i<4;i++){
            final int slotIndex=i;
            TextView slot=typeText("·",36,true);
            slot.setGravity(Gravity.CENTER);
            slot.setTextColor(i==activeSlot?BRASS_LIGHT:Color.rgb(230,205,151));
            slot.setBackgroundColor(Color.TRANSPARENT);

            if(answerSlots[i]!='\0'){
                slot.setText(styledDigits(String.valueOf(answerSlots[i]),false));
                if(i==activeSlot)slot.setTextColor(BRASS_LIGHT);
            }

            slot.setOnClickListener(v->{
                activeSlot=slotIndex;
                renderWorking();
            });

            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(68),dp(56));
            lp.setMargins(dp(5),0,dp(5),0);
            workingSlots.addView(slot,lp);
        }
    }

    void renderCandidates(){
        candidateBox.removeAllViews();

        if(candidates.isEmpty()){
            TextView empty=typeText("·  ·  ·  ·",17,false);
            empty.setTextColor(Color.rgb(143,102,64));
            empty.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(4),0,dp(4),0);
            candidateBox.addView(empty,new ViewGroup.LayoutParams(-2,dp(38)));
            return;
        }

        for(String value:candidates){
            final String candidate=value;
            final ScratchTextView item=new ScratchTextView(this);
            item.setTextSize(18);
            item.setTypeface(oldFace(true));
            item.setTextColor(INK);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(3),0,dp(10),0);
            item.setText(styledDigits(candidate+",",false));
            item.setScratched(crossedCandidates.contains(candidate));

            item.setOnClickListener(v->{
                if(crossedCandidates.contains(candidate))crossedCandidates.remove(candidate);
                else crossedCandidates.add(candidate);
                item.setScratched(crossedCandidates.contains(candidate));
            });

            candidateBox.addView(item,new ViewGroup.LayoutParams(-2,dp(39)));
        }
    }
    void saveCandidate(){
        String value=currentPartial();

        if(value.length()==0){
            flashStamp("NO CIPHER",false);
            return;
        }

        if(!candidates.contains(value))candidates.add(value);

        clearAnswerSlots();
        renderWorking();
        renderCandidates();
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
        refreshAllMarks();
    }

    void refreshAllMarks(){
        for(int d=0;d<=9;d++){
            Button k=digitKeys[d];
            if(k==null)continue;
            char c=(char)('0'+d);
            boolean off=eliminated.contains(c);
            k.setBackground(keyBackground(off));
            k.setTextColor(off?Color.rgb(183,149,112):INK);
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

        for(Clue c:puzzle.clues){
            visibleClues.add(c);

            LinearLayout card=new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(9),dp(3),dp(8),dp(3));
            card.setBackground(new AgedPaperDrawable(8,true));

            TextView code=typeText("",22,true);
            code.setGravity(Gravity.CENTER_VERTICAL);
            code.setText(styledDigits(c.code,false));
            clueCodeViews.add(code);
            card.addView(code,new LinearLayout.LayoutParams(dp(92),dp(35)));

            TextView rule=typeText(c.shortText(),10,true);
            rule.setTextColor(FADED);
            rule.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(rule,new LinearLayout.LayoutParams(0,dp(35),1));

            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(40));
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
        if(assistBtn!=null)assistBtn.setEnabled(false);
        if(decipherBtn!=null)decipherBtn.setEnabled(false);

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
            });
        });
    }

    void useAssist(){
        if(puzzle==null||assistUsed)return;

        if(mode.equals("hard")&&!attempted){
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
        refreshAllMarks();
    }

    void checkWorking(){
        if(puzzle==null||solved)return;

        String guess=currentGuess();

        if(guess==null){
            flashStamp("4 DIGITS REQUIRED",false);
            return;
        }

        HashSet<Character> unique=new HashSet<>();
        for(char c:answerSlots)unique.add(c);

        if(unique.size()!=4){
            flashStamp("NO REPEATS",false);
            return;
        }

        attempted=true;

        if(guess.equals(puzzle.secret)){
            solved=true;
            decipherBtn.setEnabled(false);
            showSolvedPopup();
        }else{
            flashStamp("CIPHER REJECTED",false);
        }
    }

    void showSolvedPopup(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24),dp(22),dp(24),dp(20));
        panel.setBackground(new AgedPaperDrawable(12,true));

        TextView title=typeText("CIPHER BROKEN",27,true);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(RED_INK);
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));

        TextView code=typeText(new String(answerSlots),29,true);
        code.setGravity(Gravity.CENTER);
        code.setTextColor(INK);
        panel.addView(code,new LinearLayout.LayoutParams(-1,dp(44)));

        Button next=oldButton("NEW CIPHER");
        next.setTextSize(12);
        next.setTextColor(INK);
        next.setBackground(new MechanicalKeyDrawable(false,false));
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,dp(44));
        nlp.setMargins(0,dp(12),0,0);
        panel.addView(next,nlp);

        final PopupWindow popup=new PopupWindow(panel,dp(310),dp(205),true);
        popup.setBackgroundDrawable(rounded(PAPER,PAPER_DARK,12));
        popup.setOutsideTouchable(false);
        if(Build.VERSION.SDK_INT>=21)popup.setElevation(dp(12));

        next.setOnClickListener(v->{
            popup.dismiss();
            startPuzzle();
        });

        popup.showAtLocation(root,Gravity.CENTER,0,0);
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
            int h4r=m.equals("hard")?1:0;

            int h3min=m.equals("easy")?2:(m.equals("hard")?10:6);
            int h3max=m.equals("easy")?6:(m.equals("hard")?18:12);
            int h4min=m.equals("hard")?3:2;
            int h4max=m.equals("easy")?3:(m.equals("hard")?6:4);

            ArrayList<Clue> p3=poolFor(secret,h3t,h3r);
            ArrayList<Clue> p4=poolFor(secret,2,h4r);
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

    @Override protected void onDestroy(){
        puzzlePool.shutdownNow();
        super.onDestroy();
    }
}
