//
// ZipUtils.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    private List<String> fileList;
    private File zipSourceDir;

    public ZipUtils() {
        fileList = new ArrayList<String>();
    }

    public void zip(File sourceDir, File targetZipFile) {
        zipSourceDir = sourceDir;
        fileList.clear();
        generateFileList(zipSourceDir);
        zipIt(targetZipFile);
    }

    private void zipIt(File zipFile) {
        byte[] buffer = new byte[1024];
        String source = zipSourceDir.getName();
        try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (String file : this.fileList) {
                ZipEntry ze = new ZipEntry(source + "/" + file);
                zos.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(new File(zipSourceDir, file))) {
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
            }
            zos.closeEntry();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void generateFileList(File node) {
        if (node.isFile()) {
            fileList.add(generateZipEntry(node.toString()));
        }
        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename : subNote) {
                generateFileList(new File(node, filename));
            }
        }
    }

    private String generateZipEntry(String file) {
        String zipEntry = file.substring(zipSourceDir.getPath().length() + 1, file.length());
        zipEntry = zipEntry.replace("\\", "/");
        return zipEntry;
    }

    public void unzip(File sourceZipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceZipFile))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                        int bufferSize = Math.toIntExact(entry.getSize());
                        if (bufferSize <= 0) {
                            bufferSize = 4096;
                        }
                        byte[] buffer = new byte[bufferSize];
                        int location;
                        while ((location = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, location);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
        }
    }
}