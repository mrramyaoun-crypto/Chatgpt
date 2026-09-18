package com.codebreaker.game;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(248,243,236), INK=Color.rgb(44,49,58), MUTED=Color.rgb(108,116,128);
    static final int BLUE=Color.rgb(126,82,222), GREEN=Color.rgb(52,168,113), ORANGE=Color.rgb(227,122,84);
    static final int LINE=Color.rgb(225,218,210), WHITE=Color.WHITE, SOFT=Color.rgb(252,249,245);

    final Random rnd=new Random();
    final ArrayList<String> ALL=new ArrayList<>();
    final ExecutorService pool=Executors.newSingleThreadExecutor();
    final HashSet<Character> crossed=new HashSet<>();

    ScrollView appScroll;
    LinearLayout root, cluesBox, digitRow;
    EditText guess, notes;
    TextView status, modeInfo;
    Button hintBtn, newBtn, difficultyBtn;
    String mode="normal";
    Puzzle puzzle;
    boolean hintUsed=false, attempted=false;

    static class Score { int total,right; Score(int t,int r){total=t;right=r;} }
    static class Clue {
        String code; int total,right;
        Clue(String c,int t,int r){code=c;total=t;right=r;}
        String text(){
            if(total==1&&right==1)return "One digit is correct and in the right place.";
            if(total==1)return "One digit is correct but in the wrong place.";
            if(total==2&&right==0)return "Two digits are correct, both in the wrong places.";
            return "Two digits are correct: one is in the right place and one is in the wrong place.";
        }
    }
    static class Puzzle { String secret; ArrayList<Clue> clues; Puzzle(String s,ArrayList<Clue> c){secret=s;clues=c;} }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        genCodes("",new boolean[10]);
        buildUi();
        startPuzzle();
    }

    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    int statusBarHeight(){
        int id=getResources().getIdentifier("status_bar_height","dimen","android");
        return id>0?getResources().getDimensionPixelSize(id):dp(24);
    }
    int accent(){
        if(mode.equals("easy")) return GREEN;
        if(mode.equals("hard")) return ORANGE;
        return BLUE;
    }
    int pale(){
        if(mode.equals("easy")) return Color.rgb(234,248,240);
        if(mode.equals("hard")) return Color.rgb(254,239,234);
        return Color.rgb(242,236,253);
    }
    GradientDrawable box(int fill,int stroke,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius));
        if(stroke!=Color.TRANSPARENT) g.setStroke(dp(1),stroke);
        return g;
    }
    TextView tv(String s,int sp,boolean bold){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(INK);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    Button btn(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(12); b.setAllCaps(false);
        b.setMinHeight(0); b.setMinWidth(0);
        b.setPadding(dp(8),0,dp(8),0);
        b.setTextColor(INK);
        b.setBackground(box(WHITE,LINE,12));
        return b;
    }
    void stylePrimary(Button b){
        b.setTextColor(WHITE); b.setBackground(box(accent(),accent(),12));
    }
    void genCodes(String p,boolean[] used){
        if(p.length()==4){ALL.add(p);return;}
        for(int i=0;i<10;i++) if(!used[i]){
            used[i]=true; genCodes(p+i,used); used[i]=false;
        }
    }

    void buildUi(){
        appScroll=new ScrollView(this);
        appScroll.setFillViewport(true); appScroll.setBackgroundColor(BG);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusableInTouchMode(true);
        root.setPadding(dp(10),statusBarHeight()+dp(8),dp(10),dp(12));
        appScroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        setContentView(appScroll);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title=tv("Code Breaker",20,true);
        TextView sub=tv("Crack the code with 5 logical hints",10,false); sub.setTextColor(MUTED);
        titleBox.addView(title); titleBox.addView(sub);
        header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout puzzleActions=new LinearLayout(this);
        puzzleActions.setGravity(Gravity.CENTER_VERTICAL);

        newBtn=btn("New Puzzle"); stylePrimary(newBtn);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(dp(104),dp(42));
        puzzleActions.addView(newBtn,nlp);

        difficultyBtn=btn("+");
        difficultyBtn.setTextSize(19);
        difficultyBtn.setTextColor(WHITE);
        difficultyBtn.setBackground(box(accent(),accent(),12));
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dp(44),dp(42));
        dlp.setMargins(dp(5),0,0,0);
        puzzleActions.addView(difficultyBtn,dlp);

        LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-2,-2);
        actionsLp.setMargins(dp(8),0,0,0);
        header.addView(puzzleActions,actionsLp);
        root.addView(header);

        newBtn.setOnClickListener(v->startPuzzle());
        difficultyBtn.setOnClickListener(v->showDifficultyMenu());
        refreshModeStyles();

        cluesBox=new LinearLayout(this); cluesBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cbp=new LinearLayout.LayoutParams(-1,-2); cbp.setMargins(0,dp(8),0,dp(8));
        root.addView(cluesBox,cbp);

        LinearLayout work=new LinearLayout(this); work.setOrientation(LinearLayout.VERTICAL);
        work.setPadding(dp(9),dp(9),dp(9),dp(8)); work.setBackground(box(WHITE,LINE,16));
        root.addView(work,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout answer=new LinearLayout(this); answer.setGravity(Gravity.CENTER_VERTICAL);
        guess=new EditText(this); guess.setSingleLine(); guess.setTextSize(19); guess.setGravity(Gravity.CENTER);
        guess.setHint("4 digits"); guess.setTextColor(INK); guess.setHintTextColor(Color.rgb(130,143,157));
        guess.setBackground(box(Color.rgb(250,252,255),Color.rgb(191,211,233),11));
        guess.setInputType(InputType.TYPE_CLASS_NUMBER); guess.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        answer.addView(guess,new LinearLayout.LayoutParams(0,dp(44),1));

        hintBtn=btn("Hint"); hintBtn.setTextColor(Color.rgb(135,96,0));
        hintBtn.setBackground(box(Color.rgb(255,247,216),Color.rgb(237,215,139),11));
        LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(dp(70),dp(44)); hlp.setMargins(dp(6),0,0,0); answer.addView(hintBtn,hlp);

        Button check=btn("Check"); stylePrimary(check);
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(dp(72),dp(44)); clp.setMargins(dp(6),0,0,0); answer.addView(check,clp);
        work.addView(answer);
        hintBtn.setOnClickListener(v->useHint()); check.setOnClickListener(v->checkGuess());

        status=tv("Generating…",11,false); status.setTextColor(MUTED); status.setPadding(dp(2),dp(6),0,dp(4)); work.addView(status);

        modeInfo=tv("Normal · balanced deduction",11,true); modeInfo.setTextColor(BLUE);
        modeInfo.setPadding(dp(9),dp(5),dp(9),dp(5)); modeInfo.setBackground(box(Color.rgb(234,242,253),Color.TRANSPARENT,10));
        LinearLayout.LayoutParams mip=new LinearLayout.LayoutParams(-2,-2); mip.setMargins(0,0,0,dp(7)); work.addView(modeInfo,mip);

        digitRow=new LinearLayout(this); digitRow.setGravity(Gravity.CENTER);
        work.addView(digitRow,new LinearLayout.LayoutParams(-1,dp(38))); renderDigits();

        notes=new EditText(this); notes.setHint("Notes / deductions…"); notes.setGravity(Gravity.TOP|Gravity.START); notes.setTextSize(14);
        notes.setTextColor(INK); notes.setHintTextColor(Color.rgb(124,137,151));
        notes.setBackground(box(Color.rgb(252,253,255),Color.rgb(216,224,233),11));
        notes.setPadding(dp(9),dp(7),dp(9),dp(7));
        notes.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams notesLp=new LinearLayout.LayoutParams(-1,dp(96)); notesLp.setMargins(0,dp(7),0,dp(5));
        work.addView(notes,notesLp);
        notes.setOnFocusChangeListener((v,hasFocus)->{
            if(hasFocus){
                appScroll.postDelayed(()->{
                    Rect r=new Rect();
                    notes.getDrawingRect(r);
                    root.offsetDescendantRectToMyCoords(notes,r);
                    int target=Math.max(0,r.bottom-appScroll.getHeight()+dp(28));
                    appScroll.smoothScrollTo(0,target);
                },300);
            }
        });
        notes.setOnClickListener(v->appScroll.postDelayed(()->{
            Rect r=new Rect();
            notes.getDrawingRect(r);
            root.offsetDescendantRectToMyCoords(notes,r);
            int target=Math.max(0,r.bottom-appScroll.getHeight()+dp(28));
            appScroll.smoothScrollTo(0,target);
        },250));

        Button clear=btn("Clear scratchpad");
        clear.setTextColor(accent()); clear.setBackground(box(pale(),Color.TRANSPARENT,10));
        clear.setOnClickListener(v->{crossed.clear();notes.setText("");renderDigits();});
        work.addView(clear,new LinearLayout.LayoutParams(-1,dp(38)));
    }

    void refreshModeStyles(){
        int a=accent();
        if(newBtn!=null)stylePrimary(newBtn);
        if(difficultyBtn!=null){
            difficultyBtn.setTextColor(WHITE);
            difficultyBtn.setBackground(box(a,a,12));
        }
        if(modeInfo!=null){
            modeInfo.setText(mode.equals("easy")?"Easy · extra help in Hint 3":mode.equals("hard")?"Hard · one clue tougher":"Normal · classic clue pattern");
            modeInfo.setTextColor(a);
            modeInfo.setBackground(box(pale(),Color.TRANSPARENT,10));
        }
    }

    void showDifficultyMenu(){
        PopupMenu menu=new PopupMenu(this,difficultyBtn);
        menu.getMenu().add("Easy");
        menu.getMenu().add("Normal");
        menu.getMenu().add("Hard");
        menu.setOnMenuItemClickListener(item->{
            String next=item.getTitle().toString().toLowerCase();
            if(!next.equals(mode)){
                mode=next;
                refreshModeStyles();
                startPuzzle();
            }
            return true;
        });
        menu.show();
    }

    void renderDigits(){
        if(digitRow==null)return;
        digitRow.removeAllViews();
        for(char c='0';c<='9';c++){
            final char d=c; Button b=btn(String.valueOf(c)); boolean off=crossed.contains(c);
            b.setTextSize(13);
            b.setTextColor(off?Color.rgb(155,160,166):INK);
            b.setBackground(box(off?Color.rgb(236,240,244):Color.rgb(250,252,255),LINE,8));
            if(off)b.setText("×"+c);
            b.setOnClickListener(v->{if(crossed.contains(d))crossed.remove(d);else crossed.add(d);renderDigits();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(34),1); lp.setMargins(dp(1),0,dp(1),0);
            digitRow.addView(b,lp);
        }
    }

    void renderPuzzle(){
        cluesBox.removeAllViews();
        for(int i=0;i<puzzle.clues.size();i++){
            Clue c=puzzle.clues.get(i);
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(7),dp(9),dp(7));
            row.setMinimumHeight(dp(58));
            row.setBackground(box(Color.rgb(255,253,250),LINE,15));

            View strip=new View(this);
            strip.setBackground(box(accent(),Color.TRANSPARENT,5));
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(4),dp(40));
            sp.setMargins(0,0,dp(9),0);
            row.addView(strip,sp);

            TextView code=tv(c.code,22,true);
            code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            code.setTextColor(Color.rgb(57,52,68));
            code.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout right=new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            TextView label=tv("Hint "+(i+1),11,true);
            label.setTextColor(accent());
            TextView text=tv(c.text(),12,false);
            text.setTextColor(Color.rgb(67,70,78));
            text.setLineSpacing(0f,1.04f);
            right.addView(label);
            right.addView(text);

            row.addView(code,new LinearLayout.LayoutParams(dp(82),-2));
            row.addView(right,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,0,0,dp(5));
            cluesBox.addView(row,lp);
        }
    }

    void startPuzzle(){
        newBtn.setEnabled(false); hintBtn.setEnabled(false); status.setText("Generating fresh puzzle…");
        guess.setText(""); guess.setEnabled(false); crossed.clear(); notes.setText(""); renderDigits(); attempted=false; hintUsed=false;
        pool.submit(()->{
            Puzzle p=generate(mode);
            runOnUiThread(()->{
                puzzle=p; renderPuzzle(); newBtn.setEnabled(true); hintBtn.setEnabled(true); hintBtn.setText("Hint");
                guess.setEnabled(true); refreshModeStyles();
                status.setText("Use the scratchpad below to eliminate digits.");
                root.requestFocus();
                appScroll.post(()->appScroll.smoothScrollTo(0,0));
            });
        });
    }

    Score score(String secret,String clue){
        int total=0,right=0;
        for(int i=0;i<4;i++){if(secret.charAt(i)==clue.charAt(i))right++;if(clue.indexOf(secret.charAt(i))>=0)total++;}
        return new Score(total,right);
    }
    boolean matches(String candidate,Clue c){Score s=score(candidate,c.code);return s.total==c.total&&s.right==c.right;}
    ArrayList<String> filter(List<String> in,List<Clue> clues){
        ArrayList<String> out=new ArrayList<>();
        outer:for(String s:in){for(Clue c:clues)if(!matches(s,c))continue outer;out.add(s);}return out;
    }
    boolean disjoint(String a,String b){for(int i=0;i<4;i++)if(b.indexOf(a.charAt(i))>=0)return false;return true;}
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
            used.add(c1.code);used.add(c2.code);

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
            clues.add(c3);used.add(c3.code);
            cand=filter(cand,Collections.singletonList(c3));

            Clue c4=chooseByCount(p4,cand,h4min,h4max,used);
            if(c4==null)continue;
            clues.add(c4);used.add(c4.code);
            cand=filter(cand,Collections.singletonList(c4));

            Clue c5=chooseByCount(p5,cand,1,1,used);
            if(c5==null)continue;
            clues.add(c5);
            cand=filter(cand,Collections.singletonList(c5));

            if(cand.size()!=1||!cand.get(0).equals(secret))continue;

            Score s1=score(secret,c1.code),s2=score(secret,c2.code),s3=score(secret,c3.code),s4=score(secret,c4.code),s5=score(secret,c5.code);
            boolean pattern=s1.total==1&&s1.right==1&&s2.total==1&&s2.right==1&&disjoint(c1.code,c2.code)
                    &&s3.total==h3t&&s3.right==h3r&&containsExactlyOne(c3.code,d1,d2)
                    &&s4.total==2&&s4.right==h4r
                    &&s5.total==2&&s5.right==0;
            if(pattern)return new Puzzle(secret,clues);
        }
    }
    void useHint(){
        if(puzzle==null||hintUsed)return;
        if(mode.equals("hard")&&!attempted){status.setText("Hard mode: make one guess before using Hint.");return;}
        ArrayList<Character> falseDigits=new ArrayList<>();
        for(char d='0';d<='9';d++)if(puzzle.secret.indexOf(d)<0&&!crossed.contains(d))falseDigits.add(d);
        Collections.shuffle(falseDigits,rnd);
        int n=mode.equals("easy")?Math.min(2,falseDigits.size()):Math.min(1,falseDigits.size());
        for(int i=0;i<n;i++)crossed.add(falseDigits.get(i));
        renderDigits();hintUsed=true;hintBtn.setEnabled(false);hintBtn.setText("Used");
        status.setText(n==2?"Hint removed 2 digits that are not in the code.":"Hint removed 1 digit that is not in the code.");
    }
    void checkGuess(){
        if(puzzle==null)return;
        String g=guess.getText().toString().trim();
        if(g.length()!=4){status.setText("Enter exactly 4 digits.");return;}
        HashSet<Character> s=new HashSet<>();for(char c:g.toCharArray())s.add(c);
        if(s.size()!=4){status.setText("Use 4 different digits.");return;}
        attempted=true;
        if(g.equals(puzzle.secret)){status.setText("🔓 Correct! You cracked it.");guess.setEnabled(false);}
        else{status.setText("Not the code — keep going.");guess.selectAll();}
    }
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
