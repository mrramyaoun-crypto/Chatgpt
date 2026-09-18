package com.codebreaker.game;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BLUE=Color.rgb(23,59,103), BG=Color.rgb(244,246,248), LINE=Color.rgb(220,224,229), MUTED=Color.rgb(93,103,115);
    final Random rnd=new Random();
    final ArrayList<String> ALL=new ArrayList<>();
    final ExecutorService pool=Executors.newSingleThreadExecutor();
    LinearLayout root, cluesBox, digitRow; EditText guess, notes; TextView status; Button hintBtn, newBtn;
    String mode="normal"; Puzzle puzzle; boolean hintUsed=false; boolean attempted=false; final HashSet<Character> crossed=new HashSet<>();

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
        super.onCreate(b); genCodes("", new boolean[10]); buildUi(); startPuzzle();
    }
    void genCodes(String p, boolean[] used){
        if(p.length()==4){ALL.add(p);return;}
        for(int i=0;i<10;i++) if(!used[i]){used[i]=true;genCodes(p+i,used);used[i]=false;}
    }
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    TextView tv(String s,int sp,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.DKGRAY);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setMinHeight(0); b.setMinWidth(0);
        b.setPadding(dp(8),0,dp(8),0); return b;
    }
    void buildUi(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8),dp(8),dp(8),dp(8));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2)); setContentView(scroll);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        String[] ms={"Easy","Normal","Hard"};
        for(String m:ms){
            Button b=btn(m); b.setTag(m.toLowerCase()); if(m.equals("Normal")){b.setTextColor(Color.WHITE);b.setBackgroundColor(BLUE);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1); lp.setMargins(dp(2),0,dp(2),0); top.addView(b,lp);
            b.setOnClickListener(v->{mode=(String)v.getTag(); for(int i=0;i<3;i++){Button x=(Button)top.getChildAt(i); boolean a=x.getTag().equals(mode); x.setTextColor(a?Color.WHITE:Color.DKGRAY); x.setBackgroundColor(a?BLUE:Color.LTGRAY);} startPuzzle();});
        }
        newBtn=btn("New Puzzle"); newBtn.setTextColor(Color.WHITE); newBtn.setBackgroundColor(BLUE);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(dp(105),dp(38)); nlp.setMargins(dp(4),0,0,0); top.addView(newBtn,nlp); root.addView(top);
        newBtn.setOnClickListener(v->startPuzzle());

        cluesBox=new LinearLayout(this); cluesBox.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cbp=new LinearLayout.LayoutParams(-1,-2); cbp.setMargins(0,dp(6),0,dp(4)); root.addView(cluesBox,cbp);

        LinearLayout answer=new LinearLayout(this); answer.setGravity(Gravity.CENTER_VERTICAL);
        guess=new EditText(this); guess.setSingleLine(); guess.setTextSize(18); guess.setGravity(Gravity.CENTER); guess.setHint("4 digits");
        guess.setInputType(InputType.TYPE_CLASS_NUMBER); guess.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        answer.addView(guess,new LinearLayout.LayoutParams(0,dp(46),1));
        hintBtn=btn("Hint"); LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(dp(70),dp(46)); hlp.setMargins(dp(4),0,0,0); answer.addView(hintBtn,hlp);
        Button check=btn("Check"); LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(dp(70),dp(46)); clp.setMargins(dp(4),0,0,0); answer.addView(check,clp); root.addView(answer);
        hintBtn.setOnClickListener(v->useHint()); check.setOnClickListener(v->checkGuess());

        status=tv("Generating…",11,false); status.setTextColor(MUTED); status.setPadding(dp(2),dp(3),0,dp(3)); root.addView(status);

        digitRow=new LinearLayout(this); digitRow.setGravity(Gravity.CENTER); root.addView(digitRow,new LinearLayout.LayoutParams(-1,dp(38))); renderDigits();

        notes=new EditText(this); notes.setHint("Notes / deductions…"); notes.setGravity(Gravity.TOP|Gravity.START); notes.setTextSize(14);
        notes.setBackgroundColor(Color.WHITE); notes.setPadding(dp(8),dp(6),dp(8),dp(6)); notes.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(notes,new LinearLayout.LayoutParams(-1,dp(130)));

        Button clear=btn("Clear scratchpad"); clear.setOnClickListener(v->{crossed.clear();notes.setText("");renderDigits();});
        root.addView(clear,new LinearLayout.LayoutParams(-1,dp(36)));
    }
    void renderDigits(){
        digitRow.removeAllViews();
        for(char c='0';c<='9';c++){
            final char d=c; Button b=btn(String.valueOf(c)); boolean off=crossed.contains(c);
            b.setTextColor(off?Color.GRAY:Color.DKGRAY); b.setBackgroundColor(off?Color.rgb(232,234,237):Color.WHITE);
            if(off)b.setText("✕"+c);
            b.setOnClickListener(v->{if(crossed.contains(d))crossed.remove(d);else crossed.add(d);renderDigits();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(34),1); lp.setMargins(dp(1),0,dp(1),0); digitRow.addView(b,lp);
        }
    }
    void renderPuzzle(){
        cluesBox.removeAllViews();
        for(int i=0;i<puzzle.clues.size();i++){
            Clue c=puzzle.clues.get(i);
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(8),dp(5),dp(8),dp(5)); row.setBackgroundColor(Color.WHITE);
            TextView code=tv(c.code,20,true); code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); code.setGravity(Gravity.CENTER_VERTICAL);
            TextView text=tv("Hint "+(i+1)+"\n"+c.text(),11,false); text.setTextColor(MUTED); text.setPadding(dp(12),0,0,0);
            row.addView(code,new LinearLayout.LayoutParams(dp(90),-2)); row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(2),0,dp(2)); cluesBox.addView(row,lp);
        }
    }
    void startPuzzle(){
        newBtn.setEnabled(false); hintBtn.setEnabled(false); status.setText("Generating fresh puzzle…"); guess.setText(""); guess.setEnabled(false);
        crossed.clear(); notes.setText(""); renderDigits(); attempted=false; hintUsed=false;
        pool.submit(()->{
            Puzzle p=generate(mode);
            runOnUiThread(()->{
                puzzle=p; renderPuzzle(); newBtn.setEnabled(true); hintBtn.setEnabled(true); hintBtn.setText("Hint");
                guess.setEnabled(true); status.setText(mode.equals("easy")?"Easy · direct elimination":mode.equals("hard")?"Hard · one step more deduction":"Normal · balanced deduction");
                guess.requestFocus();
            });
        });
    }
    Score score(String secret,String clue){
        int total=0,right=0;
        for(int i=0;i<4;i++){ if(secret.charAt(i)==clue.charAt(i))right++; if(clue.indexOf(secret.charAt(i))>=0)total++; }
        return new Score(total,right);
    }
    boolean matches(String candidate,Clue c){Score s=score(candidate,c.code);return s.total==c.total&&s.right==c.right;}
    ArrayList<String> filter(List<String> in,List<Clue> clues){
        ArrayList<String> out=new ArrayList<>();
        outer: for(String s:in){for(Clue c:clues)if(!matches(s,c))continue outer;out.add(s);} return out;
    }
    boolean disjoint(String a,String b){for(int i=0;i<4;i++)if(b.indexOf(a.charAt(i))>=0)return false;return true;}
    ArrayList<Clue> poolFor(String secret,boolean fixed){
        ArrayList<Clue> p=new ArrayList<>();
        for(String code:ALL){Score s=score(secret,code); if(fixed){if(s.total==1&&s.right==1)p.add(new Clue(code,1,1));}
            else if((s.total==1&&s.right==0)||(s.total==2&&s.right==0)||(s.total==2&&s.right==1))p.add(new Clue(code,s.total,s.right));}
        Collections.shuffle(p,rnd); return p;
    }
    Clue chooseClue(ArrayList<Clue> pool,List<String> candidates,int min,int max,Set<String> used){
        for(Clue c:pool){if(used.contains(c.code))continue;int n=0;for(String s:candidates)if(matches(s,c))n++;if(n>=min&&n<=max)return c;}return null;
    }
    Puzzle generate(String m){
        int t3min,t3max,t4min,t4max;
        if(m.equals("easy")){t3min=2;t3max=4;t4min=2;t4max=2;}
        else if(m.equals("hard")){t3min=7;t3max=11;t4min=3;t4max=5;}
        else {t3min=4;t3max=7;t4min=2;t4max=3;}
        for(int attempt=0;attempt<800;attempt++){
            String secret=ALL.get(rnd.nextInt(ALL.size()));
            ArrayList<Clue> fixed=poolFor(secret,true), other=poolFor(secret,false);
            if(fixed.size()<2)continue;
            Clue c1=fixed.get(rnd.nextInt(fixed.size())), c2=null;
            for(Clue x:fixed)if(disjoint(c1.code,x.code)){c2=x;break;} if(c2==null)continue;
            ArrayList<Clue> clues=new ArrayList<>(); clues.add(c1); clues.add(c2);
            ArrayList<String> cand=filter(ALL,clues); Set<String> used=new HashSet<>();used.add(c1.code);used.add(c2.code);
            Clue c3=chooseClue(other,cand,t3min,t3max,used); if(c3==null)continue; clues.add(c3);used.add(c3.code); cand=filter(cand,Collections.singletonList(c3));
            Clue c4=chooseClue(other,cand,t4min,t4max,used); if(c4==null)continue; clues.add(c4);used.add(c4.code); cand=filter(cand,Collections.singletonList(c4));
            Clue c5=chooseClue(other,cand,1,1,used); if(c5==null)continue; clues.add(c5); cand=filter(cand,Collections.singletonList(c5));
            if(cand.size()==1&&cand.get(0).equals(secret))return new Puzzle(secret,clues);
        }
        return generateFallback();
    }
    Puzzle generateFallback(){
        while(true){
            String secret=ALL.get(rnd.nextInt(ALL.size())); ArrayList<Clue> fixed=poolFor(secret,true),other=poolFor(secret,false);
            Clue c1=fixed.get(0),c2=null;for(Clue x:fixed)if(disjoint(c1.code,x.code)){c2=x;break;}if(c2==null)continue;
            ArrayList<Clue> clues=new ArrayList<>(Arrays.asList(c1,c2)); ArrayList<String> cand=filter(ALL,clues); Set<String> u=new HashSet<>();u.add(c1.code);u.add(c2.code);
            for(int i=0;i<3&&cand.size()>1;i++){Clue best=null;int bestN=cand.size();for(Clue c:other){if(u.contains(c.code))continue;int n=0;for(String s:cand)if(matches(s,c))n++;if(n>0&&n<bestN){best=c;bestN=n;if(n==1)break;}}if(best==null)break;clues.add(best);u.add(best.code);cand=filter(cand,Collections.singletonList(best));}
            if(clues.size()==5&&cand.size()==1&&cand.get(0).equals(secret))return new Puzzle(secret,clues);
        }
    }
    void useHint(){
        if(puzzle==null||hintUsed)return;
        if(mode.equals("hard")&&!attempted){status.setText("Hard mode: make one guess before using Hint.");return;}
        ArrayList<Character> falseDigits=new ArrayList<>();for(char d='0';d<='9';d++)if(puzzle.secret.indexOf(d)<0&&!crossed.contains(d))falseDigits.add(d);
        Collections.shuffle(falseDigits,rnd); int n=mode.equals("easy")?Math.min(2,falseDigits.size()):Math.min(1,falseDigits.size());
        for(int i=0;i<n;i++)crossed.add(falseDigits.get(i));renderDigits();hintUsed=true;hintBtn.setEnabled(false);hintBtn.setText("Used");
        status.setText(n==2?"Hint removed 2 digits that are not in the code.":"Hint removed 1 digit that is not in the code.");
    }
    void checkGuess(){
        if(puzzle==null)return;String g=guess.getText().toString().trim();
        if(g.length()!=4){status.setText("Enter exactly 4 digits.");return;} HashSet<Character> s=new HashSet<>();for(char c:g.toCharArray())s.add(c);if(s.size()!=4){status.setText("Use 4 different digits.");return;}
        attempted=true;if(g.equals(puzzle.secret)){status.setText("🔓 Correct! You cracked it.");guess.setEnabled(false);}else{status.setText("Not the code — keep going.");guess.selectAll();}
    }
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
