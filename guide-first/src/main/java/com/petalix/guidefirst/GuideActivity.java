package com.petalix.guidefirst;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.util.Xml;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;

public class GuideActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Station> stations = new ArrayList<>();
    private Map<String, List<Show>> schedule = Collections.emptyMap();
    private final Pattern id = Pattern.compile("tvg-id=\"([^\"]*)\"");
    private LinearLayout root, hero, info, actions;
    private TextView title, description;
    private ListView list;
    private PlayerView video;
    private ExoPlayer player;
    private Station tuned;
    private boolean full;
    private int width, height;
    private static class Station {
        String name, url, id;
        Station(String n,String u,String i){name=n;url=u;id=i;}
    }
    private static class Show {
        long start,end; String title="";
    }
    private int dp(float px){return Math.round(px*getResources().getDisplayMetrics().density);}
    private TextView label(int size){TextView t=new TextView(this);t.setTextSize(size);t.setTextColor(-1);return t;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    private void importButton(String name,int kind){
        Button b=new Button(this);b.setText(name);actions.addView(b);
        b.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),kind));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        root=column();root.setBackgroundColor(0xff101418);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        hero=new LinearLayout(this);info=column();info.setPadding(dp(16),dp(4),dp(12),dp(4));
        info.setGravity(Gravity.CENTER_VERTICAL);title=label(22);description=label(16);title.setMaxLines(2);description.setMaxLines(2);
        title.setText("Petalix Live TV");description.setText("Open your channel playlist to begin.");
        info.addView(title);info.addView(description);hero.addView(info);
        video=new PlayerView(this);video.setUseController(false);video.setFocusable(false);hero.addView(video);
        root.addView(hero);actions=new LinearLayout(this);root.addView(actions);
        importButton("Playlist",1);importButton("TV listings",2);
        list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,i,k)->tune(i));
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int i,long k){if(i>=0 && i<stations.size())details(stations.get(i));}
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int w=r-l-root.getPaddingLeft()-root.getPaddingRight(), h=b-t-root.getPaddingTop()-root.getPaddingBottom();
            if(w!=width||h!=height){width=w;height=h;layout();if(list.getAdapter()!=null)((BaseAdapter)list.getAdapter()).notifyDataSetChanged();}
        });
        for(int kind=1;kind<=2;kind++){String u=getPreferences(0).getString("input"+kind,null);if(u!=null)load(Uri.parse(u),kind);}
    }
    private void layout(){
        boolean portrait=width<height;
        int preview=full?height:Math.min(height/2,portrait?width*9/16:height/3);
        hero.setLayoutParams(new LinearLayout.LayoutParams(-1,tuned==null?Math.min(dp(90),height/4):preview));
        video.setVisibility(tuned==null?View.GONE:View.VISIBLE);
        info.setVisibility(full?View.GONE:View.VISIBLE);
        hero.setOrientation(!full&&portrait?1:0);
        info.setLayoutParams(portrait?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-1,1));
        video.setLayoutParams(full?new LinearLayout.LayoutParams(-1,-1):portrait?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));
        actions.setVisibility(full?View.GONE:View.VISIBLE);list.setVisibility(full?View.GONE:View.VISIBLE);
    }
    private void details(Station s){title.setText(s.name);List<Show> rows=schedule.get(s.id);long now=System.currentTimeMillis();description.setText("No current listing");
        if(rows!=null)for(Show p:rows)if(p.start<=now&&now<p.end){description.setText(p.title);break;}}
    private void tune(int i){
        Station s=stations.get(i);details(s);
        if(s==tuned){full=true;layout();return;}
        tuned=s;layout();player.setMediaItem(MediaItem.fromUri(s.url));player.prepare();player.play();
    }
    @Override public boolean onKeyDown(int k,KeyEvent e){
        if(full&&(k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_CHANNEL_DOWN||k==KeyEvent.KEYCODE_DPAD_DOWN)&&!stations.isEmpty()){
            int delta=(k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_DPAD_UP)?1:-1;
            tune(Math.floorMod(stations.indexOf(tuned)+delta,stations.size()));return true;}
        return super.onKeyDown(k,e);
    }
    @Override public void onBackPressed(){if(full){full=false;layout();list.requestFocus();list.setSelection(stations.indexOf(tuned));}else super.onBackPressed();}
    @Override public void onStart(){super.onStart();player=new ExoPlayer.Builder(this).build();video.setPlayer(player);
        if(tuned!=null){player.setMediaItem(MediaItem.fromUri(tuned.url));player.prepare();player.play();}}
    @Override public void onStop(){video.setPlayer(null);player.release();super.onStop();}
    @Override public void onDestroy(){io.shutdownNow();super.onDestroy();}
    @Override public void onActivityResult(int kind,int result,Intent data){super.onActivityResult(kind,result,data);
        if(result==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);load(u,kind);}}
    private void load(Uri u,int kind){
        io.submit(()->{try(InputStream in=getContentResolver().openInputStream(u)){
            List<Station> next=kind==1?playlist(in):null;
            Map<String,List<Show>> shows=kind==2?listings(in):null;
            runOnUiThread(()->{if(isDestroyed())return;if(next!=null){stations.clear();stations.addAll(next);tuned=null;if(player!=null)player.stop();}
                if(shows!=null)schedule=shows;getPreferences(0).edit().putString("input"+kind,u.toString()).apply();refresh();});
        }catch(Exception ex){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"Cannot read "+(kind==1?"playlist":"TV listings"),Toast.LENGTH_LONG).show();});}});
    }
    private List<Station> playlist(InputStream in)throws IOException{
        List<Station> out=new ArrayList<>();BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));
        String line,name="",key="";while((line=r.readLine())!=null){line=line.trim();
            if(line.startsWith("#EXTINF")){name=line.substring(line.lastIndexOf(',')+1).trim();Matcher m=id.matcher(line);key=m.find()?m.group(1):"";}
            else if(line.startsWith("https://")||line.startsWith("http://")){out.add(new Station(name.isEmpty()?"Channel "+(1+out.size()):name,line,key));name="";key="";}}
        if(out.isEmpty())throw new IOException();out.sort((a,b)->a.name.compareToIgnoreCase(b.name));return out;
    }
    private Map<String,List<Show>> listings(InputStream in)throws Exception{
        Map<String,List<Show>> out=new HashMap<>();XmlPullParser x=Xml.newPullParser();x.setInput(in,null);
        SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);long now=System.currentTimeMillis();Show p=null;
        for(int t=x.getEventType();t!=XmlPullParser.END_DOCUMENT;t=x.next()){
            if(t==XmlPullParser.END_TAG&&"programme".equals(x.getName()))p=null;
            if(t!=XmlPullParser.START_TAG)continue;
            if("programme".equals(x.getName())){p=null;try{Show item=new Show();item.start=fmt.parse(x.getAttributeValue(null,"start")).getTime();item.end=fmt.parse(x.getAttributeValue(null,"stop")).getTime();
                if(item.end>now){out.computeIfAbsent(x.getAttributeValue(null,"channel"),key->new ArrayList<>()).add(item);p=item;}
            }catch(java.text.ParseException|NullPointerException ignored){}}
            else if(p!=null&&"title".equals(x.getName()))p.title=x.nextText();
        }for(List<Show> entries:out.values())entries.sort((a,b)->Long.compare(a.start,b.start));return out;
    }
    private void refresh(){
        layout();
        list.setAdapter(new BaseAdapter(){public int getCount(){return stations.size();}public Object getItem(int n){return stations.get(n);}public long getItemId(int n){return n;}
            public View getView(int i,View old,ViewGroup parent){Station s=stations.get(i);LinearLayout row=new LinearLayout(GuideActivity.this);row.setMinimumHeight(dp(46));row.setGravity(Gravity.CENTER_VERTICAL);
                TextView station=label(16);station.setPadding(dp(12),0,dp(6),0);station.setText(s.name);row.addView(station,new LinearLayout.LayoutParams(Math.max(dp(94),width/4),-2));
                List<Show> shows=schedule.get(s.id);List<String> names=new ArrayList<>();long now=System.currentTimeMillis();
                if(shows!=null)for(Show sh:shows)if(sh.end>now&&names.size()<(width<dp(600)?1:3))names.add(sh.title);
                if(names.isEmpty())names.add("No listings");for(String n:names){TextView slot=label(15);slot.setText(n);slot.setMaxLines(2);row.addView(slot,new LinearLayout.LayoutParams(0,-2,1));}return row;}});
        if(!stations.isEmpty()){details(stations.get(0));list.requestFocus();}
    }
}
