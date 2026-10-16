package com.discnet.redblen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Safely stores independent, downsampled texture copies in private app storage. */
public final class TextureStore {
    private TextureStore(){}
    private static final int MAX_IMAGE=1024;
    public static boolean safeId(String id){
        return id!=null&&(id.isEmpty()||id.matches("[0-9a-fA-F-]{36}\\.png"));
    }
    public static File file(Context context,String id){
        if(!safeId(id)||id.isEmpty())throw new IllegalArgumentException("Invalid texture ID");
        return new File(new File(context.getFilesDir(),"textures"),id);
    }
    public static String importImage(Context context,Uri source)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream stream=context.getContentResolver().openInputStream(source)){
            if(stream==null)throw new IOException("Arquivo inacessível");
            BitmapFactory.decodeStream(stream,null,bounds);
        }
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Imagem inválida");
        int sample=1;
        while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>MAX_IMAGE)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=sample;
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try(InputStream stream=context.getContentResolver().openInputStream(source)){
            if(stream==null)throw new IOException("Arquivo inacessível");
            decoded=BitmapFactory.decodeStream(stream,null,options);
        }
        if(decoded==null)throw new IOException("Formato de imagem não suportado");
        int w=decoded.getWidth(),h=decoded.getHeight();
        Bitmap scaled=decoded;
        if(Math.max(w,h)>MAX_IMAGE){
            float scale=(float)MAX_IMAGE/Math.max(w,h);
            scaled=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);
            decoded.recycle();
        }
        String id=UUID.randomUUID().toString()+".png";
        File target=file(context,id);
        File parent=target.getParentFile();
        if(!parent.exists()&&!parent.mkdirs()){scaled.recycle();throw new IOException("Armazenamento indisponível");}
        try(FileOutputStream output=new FileOutputStream(target)){
            if(!scaled.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("Erro ao salvar imagem");
        }finally{scaled.recycle();}
        if(target.length()>5_000_000L){target.delete();throw new IOException("Imagem compactada muito grande");}
        return id;
    }
    public static byte[] readBytes(Context context,String id)throws IOException{
        if(!safeId(id)||id.isEmpty())return null;
        File file=file(context,id);
        if(!file.exists())return null;
        if(file.length()>5_000_000L)throw new IOException("Imagem muito grande");
        try(InputStream stream=new java.io.FileInputStream(file)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] chunk=new byte[8192];int count;
            while((count=stream.read(chunk))!=-1)out.write(chunk,0,count);
            return out.toByteArray();
        }
    }
    public static void writeEmbedded(Context context,String id,byte[] bytes)throws IOException{
        if(!safeId(id)||id.isEmpty()||bytes.length>5_000_000)throw new IOException("Textura incorporada inválida");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
        if(opts.outWidth<1||opts.outHeight<1||opts.outWidth>MAX_IMAGE||opts.outHeight>MAX_IMAGE)
            throw new IOException("Dimensões inválidas da textura");
        File dest=file(context,id);
        if(!dest.getParentFile().exists()&&!dest.getParentFile().mkdirs())throw new IOException("Falha ao criar pasta");
        try(FileOutputStream o=new FileOutputStream(dest)){o.write(bytes);}
    }
}
