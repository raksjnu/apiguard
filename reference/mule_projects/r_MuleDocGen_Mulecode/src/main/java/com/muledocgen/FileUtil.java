package com.muledocgen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtil {
	public static void main(String[] args) {
		FileUtil.unzip("src/main/resources/in/POC.zip", "src/main/resources/in/POC");
	}
	static ArrayList<String> filelist = new ArrayList<String>();
	
public static ArrayList<String> listFilesAndFilesSubDirectories(String directoryName,String extention) throws IOException{
        
		File directory = new File(directoryName);
  
        File[] fList = directory.listFiles();
        for (File file : fList){
            if (file.isFile() && file.getName().matches(extention)){
                filelist.add(file.getAbsolutePath());
            } else if (file.isDirectory()){
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
//
//    for (String s : c.getName().split("\\.")) {
//        dir = new File(dir, s);
//        if (dir.isDirectory() == false)
//            dir.mkdir();
//    }

    //System.out.println("Using data directory: " + dir.toString());
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
	public static void unzip1(String zipFilePath, String destDir) {
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
	public static String unzip(String zipFilePath, String destDir) {
        File dir = new File(destDir);
        //boolean parentDirectoryCreated = false;
        String projectName = null;
        // create output directory if it doesn't exist
        if(!dir.exists()) {
        	dir.mkdirs();        	
        	projectName =dir.getParent();
        }
        FileInputStream fis;
        
        //buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            
            ZipEntry ze = zis.getNextEntry();
            
           // if(ze != null)
            //	projectName = ze.getName();
            
            boolean parentDirectoryCreated=false;
            String parentDirectoryPath=null;
            while(ze != null){
            	
                String fileName = ze.getName();
                System.out.println("fileName :"+fileName);
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println("Unzipping to "+newFile.getAbsolutePath());
                //create directories for sub directories in zip
                boolean dirCreated = false;
                if(ze.isDirectory()) {
                	
                	dirCreated = new File(newFile.getPath()).mkdirs();
                	if(! parentDirectoryCreated && dirCreated) {
                		parentDirectoryCreated = true;
                		projectName = newFile.getParent().substring(3);
                	}
                	System.out.println("Is dirCreated dir : "+newFile.getPath() +"--"+dirCreated);
//                	if(!parentDirectoryCreated) {
//                		parentDirectoryCreated = true;
//                		parentDirectoryPath= newFile.getPath();
//                	}
                }else {
                	dirCreated = new File(newFile.getParent()).mkdirs();
                	if(! parentDirectoryCreated && dirCreated) {
                		parentDirectoryCreated = true;
                		projectName = newFile.getParent().substring(3);
                	}
                	System.out.println("Is dirCreated file : "+newFile.getParent() +"--"+dirCreated);
                	FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                    	fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            System.out.println("root directory : "+parentDirectoryPath);
            //close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return projectName;
    }
	
   

}