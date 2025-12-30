package com.tibcodocgen;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
public class FileUtil {
	static ArrayList<String> filelist = new ArrayList<String>();
public static ArrayList<String> listFilesAndFilesSubDirectories(String directoryName,String extention) throws IOException{
		File directory = new File(directoryName);
        File[] fList = directory.listFiles();
        for (File file : fList){
            if (file.isFile() && file.getName().matches(extention)){
                filelist.add(file.getAbsolutePath());
            } 
           else if (file.isDirectory()){
               listFilesAndFilesSubDirectories(file.getAbsolutePath(),extention);
           }
        }
        return filelist;
    }
public static String getDataDir(Class c) {
    File dir = new File(System.getProperty("user.dir"));
    dir = new File(dir, "src");
    dir = new File(dir, "main");
    dir = new File(dir, "resources");
    return dir.toString() + File.separator;
}
	public static boolean deleteDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(children[i]);
                if (!success) {
                	throw new IOException("File delete Failed");
                }
            }
        }
        return dir.delete();
    }
	public static boolean deletejunk(String junkfolder) throws IOException {
		File dir =new File(junkfolder);
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(children[i]);
                if (!success) {
                	throw new IOException("File delete Failed");
                }
            }
        }
        return dir.delete();
    }
	public static void rename(String source,String desti) throws IOException {
		File file = new File(source);
        File newFile = new File(desti);
        if(file.renameTo(newFile)){
        }else{
        	throw new IOException("File Rename Failed");
        }
	}
	public static void unzip(String zipFilePath, String destDir) {
        File dir = new File(destDir);
        if(!dir.exists()) dir.mkdirs();
        FileInputStream fis;
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
                }
                fos.close();
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
           e.printStackTrace();
        }
    }
}