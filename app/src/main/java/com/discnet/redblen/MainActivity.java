package com.discnet.redblen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Touch-first UI built with native Android widgets. No WebView and no UI framework dependency. */
public final class MainActivity extends Activity implements View3D.Listener {
    private static final int BG=0xFF111419,PANEL=0xFF1B2027,CHIP=0xFF272D36,STROKE=0xFF39424E;
    private static final int TEXT=0xFFEBF0F4,MUTED=0xFF9BA6B3,ACCENT=0xFFD9FB74;
    private static final int READ_PROJECT=201,READ_OBJ=202,WRITE_PROJECT=203,WRITE_OBJ=204,READ_TEXTURE=205;
    private Scene scene;private View3D viewport;private FrameLayout stage;private LinearLayout modes,tools,axisBar;
    private TextView selectionLabel;private View sheet;private String sheetName="";
    private int pendingTextureTarget=-1;
    private final String[] primitives={"Cubo","Esfera","Cilindro","Cone","Plano"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        scene=new Scene(true);loadAutosave();
        buildUI();refreshAll();
    }
    private void buildUI(){
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);main.setBackgroundColor(BG);
        setContentView(main);
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(14),0,dp(10),0);
        TextView title=label("Redblen",20,TEXT,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=label("0.3",11,MUTED,false);header.addView(version);
        header.addView(space(5,1));
        TextView delete=button("Excluir",false,()->{
            if(scene.current()==null){toast("Selecione um objeto");return;}
            scene.delete();refreshAll();saveAutosave();
            toast("Modelo excluído · ↶ desfaz");
        });
        delete.setContentDescription("Excluir o modelo selecionado");
        delete.setTextSize(11);
        header.addView(delete,new LinearLayout.LayoutParams(dp(63),dp(37)));
        header.addView(space(5,1));
        header.addView(button("↶",false,()->{if(scene.undo())refreshAll();else toast("Nada para desfazer");}),new LinearLayout.LayoutParams(dp(42),dp(40)));
        header.addView(button("↷",false,()->{if(scene.redo())refreshAll();else toast("Nada para refazer");}),new LinearLayout.LayoutParams(dp(42),dp(40)));
        main.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));

        modes=row();modes.setGravity(Gravity.CENTER_VERTICAL);modes.setPadding(dp(12),0,dp(12),0);
        main.addView(modes,new LinearLayout.LayoutParams(-1,dp(46)));

        stage=new FrameLayout(this);viewport=new View3D(this,scene,this);
        stage.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout info=row();info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(12),dp(7),dp(12),dp(7));
        info.setBackground(bg(PANEL,10,0));
        FrameLayout.LayoutParams hud=new FrameLayout.LayoutParams(-2,dp(34),Gravity.TOP|Gravity.LEFT);
        hud.setMargins(dp(12),dp(12),0,0);stage.addView(info,hud);
        selectionLabel=label("Cubo",12,MUTED,false);info.addView(selectionLabel);
        main.addView(stage,new LinearLayout.LayoutParams(-1,0,1));

        HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);
        tools=row();tools.setGravity(Gravity.CENTER_VERTICAL);tools.setPadding(dp(8),0,dp(8),0);
        h.addView(tools);main.addView(h,new LinearLayout.LayoutParams(-1,dp(52)));
        axisBar=row();axisBar.setGravity(Gravity.CENTER_VERTICAL);axisBar.setPadding(dp(9),0,dp(9),dp(5));
        main.addView(axisBar,new LinearLayout.LayoutParams(-1,dp(43)));
        axisBar.setVisibility(View.GONE);
        LinearLayout bar=row();bar.setGravity(Gravity.CENTER);bar.setPadding(dp(4),0,dp(4),dp(3));
        bar.setBackgroundColor(PANEL);
        addNav(bar,"＋",()->openSheet("add"));
        addNav(bar,"Cena",()->openSheet("scene"));
        addNav(bar,"Editar",()->openSheet("properties"));
        addNav(bar,"Material",()->openSheet("material"));
        addNav(bar,"Arquivo",()->openSheet("file"));
        main.addView(bar,new LinearLayout.LayoutParams(-1,dp(55)));
        renderModes();renderTools();
    }
    private void renderModes(){
        modes.removeAllViews();
        modeButton("Objeto",Scene.Mode.OBJECT);modeButton("Vértice",Scene.Mode.VERTEX);modeButton("Face",Scene.Mode.FACE);
        View spacer=new View(this);modes.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        modes.addView(button("◎ Câmera",false,()->openSheet("camera")),new LinearLayout.LayoutParams(-2,dp(34)));
    }
    private void modeButton(String name,Scene.Mode mode){
        boolean active=scene.mode==mode;
        TextView v=button(name,active,()->{
            scene.mode=mode;scene.selectedFace=-1;scene.selectedVertex=-1;
            if(mode!=Scene.Mode.OBJECT&&(scene.tool==Scene.Tool.ROTATE||scene.tool==Scene.Tool.SCALE))scene.tool=Scene.Tool.ORBIT;
            renderModes();renderTools();viewport.refresh();updateLabel();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));lp.rightMargin=dp(5);modes.addView(v,lp);
    }
    private void renderTools(){
        tools.removeAllViews();
        toolButton("Órbita",Scene.Tool.ORBIT);
        toolButton("Mover",Scene.Tool.MOVE);
        if(scene.mode==Scene.Mode.OBJECT){
            toolButton("Girar",Scene.Tool.ROTATE);
            toolButton("Escala",Scene.Tool.SCALE);
        }else if(scene.mode==Scene.Mode.FACE){
            addAction(tools,"Extrudar",()->faceOperation("extrude"));
            addAction(tools,"Inset",()->faceOperation("inset"));
            addAction(tools,"Inverter",()->faceOperation("flip"));
        }else{
            addAction(tools,"Y = 0",()->{
                Scene.Part p=scene.current();
                if(p==null||scene.selectedVertex<0){toast("Selecione um vértice");return;}
                int v=scene.selectedVertex;scene.checkpoint();
                p.mesh.moveVertex(v,0,-p.mesh.vertices[3*v+1],0);refreshAll();
            });
        }
        renderAxisBar();
    }
    private void renderAxisBar(){
        axisBar.removeAllViews();
        if(scene.mode!=Scene.Mode.OBJECT||scene.tool!=Scene.Tool.SCALE){
            axisBar.setVisibility(View.GONE);
            return;
        }
        axisBar.setVisibility(View.VISIBLE);
        TextView label=label("Eixo",11,MUTED,true);
        label.setPadding(dp(4),0,dp(8),0);
        axisBar.addView(label);
        axisOption("XYZ",Scene.ScaleAxis.ALL);
        axisOption("X",Scene.ScaleAxis.X);
        axisOption("Y",Scene.ScaleAxis.Y);
        axisOption("Z",Scene.ScaleAxis.Z);
    }
    private void axisOption(String name,Scene.ScaleAxis axis){
        TextView item=button(name,scene.scaleAxis==axis,()->{
            scene.scaleAxis=axis;renderAxisBar();updateLabel();
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(33),1);
        params.rightMargin=dp(4);
        axisBar.addView(item,params);
    }
    private void toolButton(String name,Scene.Tool tool){
        addChip(tools,name,scene.tool==tool,()->{scene.tool=tool;viewport.cancelPickAnchor();renderTools();updateLabel();});
    }
    private void addAction(LinearLayout row,String text,Runnable action){addChip(row,text,false,action);}
    private void addChip(LinearLayout row,String text,boolean highlighted,Runnable action){
        TextView v=button(text,highlighted,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));
        lp.rightMargin=dp(6);row.addView(v,lp);
    }
    private void faceOperation(String operation){
        Scene.Part p=scene.current();int face=scene.selectedFace;
        if(p==null||face<0||face>=p.mesh.faces.size()){toast("Toque em uma face primeiro");return;}
        if(p.mesh.vertexCount()>49000){toast("Malha excede o limite de edição");return;}
        scene.checkpoint();
        if(operation.equals("extrude"))scene.selectedFace=p.mesh.extrudeFace(face,0.45f);
        else if(operation.equals("inset"))scene.selectedFace=p.mesh.insetFace(face,0.22f);
        else p.mesh.flipFace(face);
        refreshAll();
    }
    private void addNav(LinearLayout bar,String title,Runnable onTap){
        TextView v=button(title,false,onTap);v.setTextSize(12);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);
        lp.setMargins(dp(3),0,dp(3),0);bar.addView(v,lp);
    }
    private void openSheet(String key){
        if(sheet!=null){stage.removeView(sheet);sheet=null;if(sheetName.equals(key)){sheetName="";return;}}
        sheetName=key;
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(bg(PANEL,17,STROKE));scroll.setElevation(dp(10));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(13),dp(16),dp(14));scroll.addView(body);
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);
        String label=key.equals("add")?"Adicionar objeto":key.equals("scene")?"Cena":key.equals("properties")?"Propriedades":key.equals("camera")?"Câmera e âncora":key.equals("material")?"Materiais":"Arquivos";
        TextView caption=label(label,16,TEXT,true);heading.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(button("Fechar ×",false,()->closeSheet()));
        body.addView(heading);
        body.addView(space(1,12));
        switch(key){
            case "add":panelAdd(body);break;
            case "scene":panelScene(body);break;
            case "properties":panelProperties(body);break;
            case "file":panelFile(body);break;
            case "camera":panelCamera(body);break;
            case "material":panelMaterial(body);break;
        }
        int height=key.equals("material")?Math.min(dp(600),(int)(getResources().getDisplayMetrics().heightPixels*.73f))
            :dp(key.equals("properties")?315:key.equals("camera")?345:260);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,height,Gravity.BOTTOM);
        lp.setMargins(dp(8),0,dp(8),dp(6));stage.addView(scroll,lp);sheet=scroll;
    }
    private void closeSheet(){if(sheet!=null)stage.removeView(sheet);sheet=null;sheetName="";}
    private void panelAdd(LinearLayout body){
        body.addView(label("PRIMITIVAS POLIGONAIS",11,MUTED,false));body.addView(space(1,10));
        for(int start=0;start<primitives.length;start+=3){
            LinearLayout r=row();
            for(int i=start;i<Math.min(start+3,primitives.length);i++){
                final String name=primitives[i];
                TextView v=button("＋ "+name,false,()->{
                    if(scene.objects.size()>=Scene.MAX_OBJECTS){toast("Limite de objetos");return;}
                    scene.add(name,true);closeSheet();refreshAll();
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);
                lp.setMargins(dp(2),dp(2),dp(2),dp(2));r.addView(v,lp);
            }
            body.addView(r);
        }
    }
    private void panelScene(LinearLayout body){
        if(scene.objects.isEmpty()){body.addView(label("Cena vazia. Adicione um objeto.",14,MUTED,false));return;}
        for(int i=0;i<scene.objects.size();i++){
            final int index=i;Scene.Part p=scene.objects.get(i);
            LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);
            TextView visible=button(p.visible?"◉":"○",false,()->{scene.checkpoint();p.visible=!p.visible;refreshAll();});
            row.addView(visible,new LinearLayout.LayoutParams(dp(46),dp(37)));
            TextView name=button(p.name,i==scene.selected,()->{scene.selected=index;scene.selectedFace=-1;scene.selectedVertex=-1;refreshAll();});
            row.addView(name,new LinearLayout.LayoutParams(0,dp(37),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));body.addView(row,lp);
        }
        body.addView(space(1,6));
        LinearLayout actions=row();
        TextView duplicate=button("Duplicar",false,()->{scene.duplicate();refreshAll();});
        TextView remove=button("Excluir",false,()->{scene.delete();refreshAll();});
        actions.addView(duplicate,new LinearLayout.LayoutParams(0,dp(40),1));
        actions.addView(space(8,1));
        actions.addView(remove,new LinearLayout.LayoutParams(0,dp(40),1));
        body.addView(actions);
    }
    private void panelProperties(LinearLayout body){
        Scene.Part p=scene.current();
        if(p==null){body.addView(label("Selecione um objeto no viewport ou na Cena.",13,MUTED,false));return;}
        EditText name=textField(p.name,false);
        body.addView(label("NOME",10,MUTED,false));body.addView(name,new LinearLayout.LayoutParams(-1,dp(42)));
        EditText[] pos=vectorFields(body,"POSIÇÃO",p.position);
        EditText[] rot=vectorFields(body,"ROTAÇÃO (GRAUS)",p.rotation);
        EditText[] scale=vectorFields(body,"ESCALA",p.scale);
        body.addView(space(1,6));
        LinearLayout actions=row();
        TextView apply=button("Aplicar valores",true,()->{
            try{
                float[] a=parseVector(pos),b=parseVector(rot),c=parseVector(scale);
                for(float val:c)if(Math.abs(val)<0.001f)throw new IllegalArgumentException();
                scene.checkpoint();p.name=name.getText().toString().trim();
                if(p.name.isEmpty())p.name="Objeto";
                p.position=a;p.rotation=b;p.scale=c;refreshAll();toast("Transformações aplicadas");
            }catch(Exception e){toast("Use números válidos; escala não pode ser zero");}
        });
        actions.addView(apply,new LinearLayout.LayoutParams(0,dp(41),1));
        actions.addView(space(7,1));
        actions.addView(button("Focar",false,()->viewport.focus()));
        body.addView(actions);body.addView(space(1,10));
        body.addView(space(1,10));
        body.addView(button("Abrir editor de materiais →",false,()->openSheet("material")),
            new LinearLayout.LayoutParams(-1,dp(44)));
    }
    private EditText[] vectorFields(LinearLayout body,String title,float[] values){
        body.addView(space(1,10));body.addView(label(title,10,MUTED,false));body.addView(space(1,3));
        LinearLayout row=row();EditText[] fields=new EditText[3];
        String[] coords={"X","Y","Z"};
        for(int i=0;i<3;i++){
            LinearLayout cell=row();cell.setGravity(Gravity.CENTER_VERTICAL);
            TextView t=label(coords[i],11,MUTED,true);t.setPadding(dp(5),0,dp(5),0);cell.addView(t);
            fields[i]=textField(String.format(Locale.US,"%.3f",values[i]),true);
            cell.addView(fields[i],new LinearLayout.LayoutParams(0,dp(40),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.rightMargin=dp(5);row.addView(cell,lp);
        }
        body.addView(row);return fields;
    }
    private float[] parseVector(EditText[] values){
        float[] out=new float[3];for(int i=0;i<3;i++){
            out[i]=Float.parseFloat(values[i].getText().toString().replace(",","."));if(!Float.isFinite(out[i])||Math.abs(out[i])>100000)throw new IllegalArgumentException();
        }
        return out;
    }
    private interface FloatSetting { void assign(float value); }

    private void panelMaterial(LinearLayout body){
        Scene.Part p=scene.current();
        if(p==null){body.addView(label("Selecione um modelo para editar o material.",13,MUTED,false));return;}
        body.addView(label("APARÊNCIA · PREDEFINIÇÕES",10,MUTED,true));
        body.addView(space(1,8));
        HorizontalScrollView strip=new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout presets=row();
        preset(presets,p,"Mate",0f,.88f,.25f,p.color,1f);
        preset(presets,p,"Plástico",0f,.28f,.7f,p.color,1f);
        preset(presets,p,"Aço",1f,.22f,.95f,0xFFB7C3C9,1f);
        preset(presets,p,"Ouro",1f,.2f,.95f,0xFFE5AD43,1f);
        preset(presets,p,"Cromo",1f,.06f,1f,0xFFD6E2ED,1f);
        preset(presets,p,"Vidro",0f,.08f,.9f,0xFFC2E6FA,.32f);
        strip.addView(presets);body.addView(strip);
        body.addView(space(1,12));
        body.addView(label("COR DO MATERIAL",10,MUTED,true));body.addView(space(1,7));
        LinearLayout colorRow=row();
        View preview=new View(this);preview.setBackground(bg(p.color,10,STROKE));
        colorRow.addView(preview,new LinearLayout.LayoutParams(dp(49),dp(43)));
        colorRow.addView(space(9,1));
        TextView choose=button(String.format(Locale.US,"Personalizar  #%06X",p.color & 0xFFFFFF),false,()->openColorPicker(p));
        colorRow.addView(choose,new LinearLayout.LayoutParams(0,dp(43),1));
        body.addView(colorRow);
        body.addView(space(1,10));
        body.addView(label("SUPERFÍCIE · AJUSTES EM TEMPO REAL",10,MUTED,true));
        body.addView(space(1,5));
        materialSlider(body,"Metalicidade",p.metallic,0,100,"%",v->p.metallic=v);
        materialSlider(body,"Rugosidade",p.roughness,3,100,"%",v->p.roughness=v);
        materialSlider(body,"Reflexão",p.reflectivity,0,100,"%",v->p.reflectivity=v);
        materialSlider(body,"Emissão",p.emission,0,200,"%",v->p.emission=v);
        materialSlider(body,"Opacidade",p.opacity,5,100,"%",v->p.opacity=v);
        body.addView(space(1,10));
        body.addView(label("TEXTURA PERSONALIZADA",10,MUTED,true));
        body.addView(space(1,6));
        LinearLayout textureActions=row();
        textureActions.addView(button(p.textureId.isEmpty()?"＋ Importar imagem":"Trocar imagem",true,()->{
            pendingTextureTarget=scene.selected;
            openDocument(READ_TEXTURE,"image/*");
        }),new LinearLayout.LayoutParams(0,dp(43),1));
        textureActions.addView(space(7,1));
        textureActions.addView(button("Remover textura",false,()->{
            if(p.textureId.isEmpty())return;
            scene.checkpoint();p.textureId="";saveAutosave();refreshAll();
        }),new LinearLayout.LayoutParams(0,dp(43),1));
        body.addView(textureActions);
        body.addView(space(1,8));
        body.addView(label(p.textureId.isEmpty()?"Nenhuma textura aplicada":"Textura carregada no material",12,MUTED,false));
        materialSlider(body,"Intensidade da textura",p.textureStrength,0,100,"%",v->p.textureStrength=v);
        materialSlider(body,"Repetição",p.textureRepeat,20,1200,"x100",v->p.textureRepeat=v);
        materialSlider(body,"Rotação",p.textureRotation,-180,180,"°",v->p.textureRotation=v);
        body.addView(space(1,12));
        body.addView(label("Reflexos simulados com iluminação de estúdio. Texturas usam projeção triplanar; não há reflexo real entre objetos.",11,MUTED,false));
    }
    private void preset(LinearLayout row,Scene.Part p,String name,float metal,float rough,float reflect,int color,float opacity){
        TextView item=button(name,false,()->{
            scene.checkpoint();
            p.metallic=metal;p.roughness=rough;p.reflectivity=reflect;
            p.color=color;p.opacity=opacity;p.emission=0f;
            viewport.refresh();saveAutosave();openSheetRefresh();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(37));
        lp.rightMargin=dp(6);row.addView(item,lp);
    }
    private void materialSlider(LinearLayout body,String name,float initial,int minimum,int maximum,
                                String suffix,FloatSetting setting){
        int progress=Math.max(minimum,Math.min(maximum,Math.round(initial*(suffix.equals("x100")?100f:suffix.equals("%")?100f:1f))));
        LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(label(name,12,TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView value=label(formatValue(progress,suffix),12,MUTED,false);title.addView(value);
        body.addView(title);
        SeekBar seek=new SeekBar(this);
        seek.setMax(maximum-minimum);
        seek.setProgress(progress-minimum);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int p,boolean fromUser){
                if(!fromUser)return;
                int actual=p+minimum;value.setText(formatValue(actual,suffix));
                setting.assign(suffix.equals("x100")?actual/100f:suffix.equals("%")?actual/100f:actual);
                viewport.refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){scene.checkpoint();}
            @Override public void onStopTrackingTouch(SeekBar bar){saveAutosave();}
        });
        body.addView(seek,new LinearLayout.LayoutParams(-1,dp(35)));
    }
    private String formatValue(int value,String suffix){
        if(suffix.equals("x100"))return String.format(Locale.US,"%.2fx",value/100f);
        return value+suffix;
    }
    private void openColorPicker(Scene.Part p){
        int initial=p.color;
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(8),dp(18),dp(3));
        View swatch=new View(this);
        swatch.setBackground(bg(initial,12,STROKE));
        box.addView(swatch,new LinearLayout.LayoutParams(-1,dp(47)));
        box.addView(space(1,10));
        EditText hex=textField(String.format(Locale.US,"#%06X",initial&0xFFFFFF),false);
        box.addView(hex,new LinearLayout.LayoutParams(-1,dp(45)));
        box.addView(space(1,8));
        int[] rgb={Color.red(initial),Color.green(initial),Color.blue(initial)};
        String[] channels={"Vermelho","Verde","Azul"};
        for(int i=0;i<3;i++){
            final int axis=i;
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(label(channels[i],12,TEXT,false),new LinearLayout.LayoutParams(dp(83),-2));
            SeekBar slider=new SeekBar(this);slider.setMax(255);slider.setProgress(rgb[i]);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onStartTrackingTouch(SeekBar bar){}
                @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                    if(!user)return;
                    rgb[axis]=value;
                    int tint=Color.rgb(rgb[0],rgb[1],rgb[2]);
                    hex.setText(String.format(Locale.US,"#%06X",tint&0xFFFFFF));
                    swatch.setBackground(bg(tint,12,STROKE));
                    p.color=tint;viewport.refresh();
                }
                @Override public void onStopTrackingTouch(SeekBar bar){}
            });
            line.addView(slider,new LinearLayout.LayoutParams(0,dp(45),1));
            box.addView(line);
        }
        scene.checkpoint();
        new AlertDialog.Builder(this).setTitle("Cor personalizada")
            .setView(box)
            .setNegativeButton("Cancelar",(d,w)->{p.color=initial;viewport.refresh();})
            .setPositiveButton("Aplicar",(d,w)->{
                try{
                    String raw=hex.getText().toString().trim();
                    if(!raw.startsWith("#"))raw="#"+raw;
                    p.color=0xFF000000|(Color.parseColor(raw)&0xFFFFFF);
                    viewport.refresh();saveAutosave();openSheetRefresh();
                }catch(IllegalArgumentException error){
                    p.color=initial;viewport.refresh();toast("Cor hexadecimal inválida");
                }
            }).show();
    }

    private void panelCamera(LinearLayout body){
        body.addView(label("NAVEGAÇÃO",10,MUTED,true));
        body.addView(space(1,7));
        LinearLayout modes=row();
        cameraOption(modes,"Órbita",Scene.Tool.ORBIT);
        cameraOption(modes,"Pan",Scene.Tool.PAN);
        cameraOption(modes,"Olhar livre",Scene.Tool.LOOK);
        body.addView(modes);
        body.addView(space(1,9));
        body.addView(label("1 dedo: modo escolhido  ·  2 dedos: pan e zoom",11,MUTED,false));
        body.addView(space(1,9));
        LinearLayout zoom=row();
        zoom.addView(button("− Afastar",false,()->viewport.dolly(1.25f)),new LinearLayout.LayoutParams(0,dp(38),1));
        zoom.addView(space(6,1));
        zoom.addView(button("＋ Aproximar",false,()->viewport.dolly(.8f)),new LinearLayout.LayoutParams(0,dp(38),1));
        zoom.addView(space(6,1));
        zoom.addView(button("Redefinir",false,()->{viewport.resetCamera();refreshAll();}),new LinearLayout.LayoutParams(0,dp(38),1));
        body.addView(zoom);
        body.addView(space(1,12));
        body.addView(label("ÂNCORA / CENTRO DA CÂMERA",10,MUTED,true));
        body.addView(space(1,5));
        LinearLayout anchorActions=row();
        anchorActions.addView(button("Escolher na cena",true,()->{
            viewport.beginPickAnchor();closeSheet();updateLabel();
            toast("Toque em um objeto ou no chão para definir o centro");
        }),new LinearLayout.LayoutParams(0,dp(39),1));
        anchorActions.addView(space(6,1));
        anchorActions.addView(button("Seleção",false,()->{
            if(!viewport.anchorSelection())toast("Selecione um objeto ou componente");
            else {closeSheet();updateLabel();}
        }),new LinearLayout.LayoutParams(0,dp(39),1));
        body.addView(anchorActions);
        EditText[] coords=vectorFields(body,"COORDENADAS DO CENTRO · X / Y / Z",viewport.anchor());
        body.addView(space(1,7));
        LinearLayout coordinates=row();
        coordinates.addView(button("Aplicar centro",true,()->{
            try{float[] v=parseVector(coords);viewport.setAnchor(v[0],v[1],v[2]);closeSheet();updateLabel();}
            catch(Exception e){toast("Coordenadas inválidas");}
        }),new LinearLayout.LayoutParams(0,dp(38),1));
        coordinates.addView(space(6,1));
        coordinates.addView(button("Origem 0,0,0",false,()->{viewport.setAnchor(0,0,0);closeSheet();updateLabel();}),
            new LinearLayout.LayoutParams(0,dp(38),1));
        body.addView(coordinates);
    }
    private void cameraOption(LinearLayout row,String name,Scene.Tool tool){
        TextView option=button(name,scene.tool==tool,()->{
            scene.tool=tool;viewport.cancelPickAnchor();closeSheet();refreshAll();
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(40),1);
        params.rightMargin=dp(5);row.addView(option,params);
    }
    private void panelFile(LinearLayout body){
        LinearLayout a=row();a.addView(button("Novo",false,()->newProject()),new LinearLayout.LayoutParams(0,dp(44),1));
        a.addView(space(7,1));a.addView(button("Salvar local",true,()->{saveAutosave();toast("Projeto salvo no app");}),new LinearLayout.LayoutParams(0,dp(44),1));
        body.addView(a);body.addView(space(1,8));
        LinearLayout b=row();
        b.addView(button("Abrir .redblen",false,()->openDocument(READ_PROJECT,"*/*")),new LinearLayout.LayoutParams(0,dp(44),1));
        b.addView(space(7,1));b.addView(button("Importar .obj",false,()->openDocument(READ_OBJ,"*/*")),new LinearLayout.LayoutParams(0,dp(44),1));
        body.addView(b);body.addView(space(1,8));
        LinearLayout c=row();
        c.addView(button("Exportar .obj",false,()->createDocument(WRITE_OBJ,"model.obj","text/plain")),new LinearLayout.LayoutParams(0,dp(44),1));
        c.addView(space(7,1));c.addView(button("Projeto .redblen",true,()->createDocument(WRITE_PROJECT,"projeto.redblen","application/json")),new LinearLayout.LayoutParams(0,dp(44),1));
        body.addView(c);
        body.addView(space(1,10));body.addView(label("Projetos ficam no armazenamento privado até serem exportados.",11,MUTED,false));
    }
    private void newProject(){
        new AlertDialog.Builder(this).setTitle("Novo projeto?")
            .setMessage("A cena atual será substituída. Exporte seu projeto antes, se necessário.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Criar",(d,w)->{
                scene.checkpoint();scene.objects.clear();scene.add("Cubo",false);
                scene.mode=Scene.Mode.OBJECT;scene.tool=Scene.Tool.ORBIT;scene.scaleAxis=Scene.ScaleAxis.ALL;
                viewport.resetCamera();closeSheet();refreshAll();saveAutosave();
            }).show();
    }
    private void openSheetRefresh(){String key=sheetName;if(sheet!=null){stage.removeView(sheet);sheet=null;sheetName="";openSheet(key);}}
    private void updateLabel(){
        Scene.Part p=scene.current();
        String suffix=scene.mode==Scene.Mode.FACE?" · Face "+(scene.selectedFace+1):scene.mode==Scene.Mode.VERTEX?" · Vértice "+(scene.selectedVertex+1):"";
        String mode=scene.tool==Scene.Tool.PAN?" · Pan":scene.tool==Scene.Tool.LOOK?" · Olhar":
            scene.tool==Scene.Tool.SCALE?" · Escala "+scene.scaleAxis.name():"";
        selectionLabel.setText(viewport.isPickingAnchor()?"Toque para ancorar":
            (p==null?"Cena":p.name+suffix)+mode);
    }
    private void refreshAll(){renderModes();renderTools();updateLabel();viewport.refresh();if(sheet!=null)openSheetRefresh();}
    @Override public void onSelectionChanged(){refreshAll();}
    @Override public void onDocumentChanged(){updateLabel();}
    @Override public void onAnchorChanged(){if(selectionLabel!=null)updateLabel();}
    private void openDocument(int request,String type){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,request);
    }
    private void createDocument(int request,String name,String type){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(request==READ_PROJECT){
                String json=readAll(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8));
                scene.checkpoint();SceneIO.decodePortableInto(scene,json,this);scene.clearHistory();closeSheet();refreshAll();saveAutosave();
                toast("Projeto aberto");
            }else if(request==READ_TEXTURE){
                if(pendingTextureTarget<0||pendingTextureTarget>=scene.objects.size())throw new Exception("Seleção inválida");
                String id=TextureStore.importImage(this,uri);
                Scene.Part target=scene.objects.get(pendingTextureTarget);
                scene.checkpoint();target.textureId=id;
                pendingTextureTarget=-1;
                saveAutosave();viewport.refresh();openSheetRefresh();
                toast("Textura aplicada");
            }else if(request==READ_OBJ){
                Mesh mesh;
                try(Reader r=new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8)){mesh=SceneIO.readObj(r);}
                if(scene.objects.size()>=Scene.MAX_OBJECTS){toast("Limite de objetos");return;}
                scene.checkpoint();scene.objects.add(new Scene.Part("OBJ importado",mesh));scene.selected=scene.objects.size()-1;
                scene.selectedVertex=-1;scene.selectedFace=-1;closeSheet();refreshAll();saveAutosave();toast("OBJ importado");
            }else if(request==WRITE_OBJ||request==WRITE_PROJECT){
                try(Writer w=new OutputStreamWriter(getContentResolver().openOutputStream(uri),StandardCharsets.UTF_8)){
                    if(request==WRITE_OBJ)SceneIO.writeObj(scene,w);else w.write(SceneIO.encodePortable(scene,this));
                }
                toast("Arquivo exportado");
            }
        }catch(Exception e){toast("Erro de arquivo: "+e.getMessage());}
    }
    private static String readAll(Reader r)throws Exception{
        try(Reader reader=r){
            StringBuilder s=new StringBuilder();char[] block=new char[4096];int n;
            while((n=reader.read(block))!=-1){s.append(block,0,n);if(s.length()>40_000_000)throw new Exception("Arquivo muito grande");}
            return s.toString();
        }
    }
    private void saveAutosave(){
        try(FileOutputStream f=openFileOutput("autosave.redblen",MODE_PRIVATE)){
            f.write(SceneIO.encode(scene).getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){toast("Não foi possível salvar: "+e.getMessage());}
    }
    private void loadAutosave(){
        try(FileInputStream f=openFileInput("autosave.redblen")){
            String json=readAll(new InputStreamReader(f,StandardCharsets.UTF_8));
            SceneIO.decodeInto(scene,json);scene.clearHistory();
        }catch(Exception ignored){} // First launch: use default cube.
    }
    @Override protected void onPause(){saveAutosave();viewport.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(viewport!=null){viewport.onResume();viewport.refresh();}}

    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private View space(int w,int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),dp(h)));return v;}
    private GradientDrawable bg(int color,float radius,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private TextView button(String text,boolean active,Runnable onClick){
        TextView v=label(text,13,active?BG:TEXT,active);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(12),0,dp(12),0);
        v.setBackground(bg(active?ACCENT:CHIP,10,0));
        v.setOnClickListener(clicked->onClick.run());return v;
    }
    private EditText textField(String value,boolean numeric){
        EditText e=new EditText(this);e.setText(value);e.setTextSize(13);e.setSingleLine(true);
        e.setTextColor(TEXT);e.setSelectAllOnFocus(true);e.setPadding(dp(10),0,dp(7),0);
        e.setBackground(bg(CHIP,8,STROKE));
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
