package org.flashdog.agent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class UnTailer implements Runnable {
    private static final String RAF_MODE = "r";
    private final byte[] inbuf;
    private final File file;
    private final long delayMillis;
    private final boolean end;
    private final LogFileTailerListener listener;
    private final boolean reOpen;
    private volatile boolean run;

    private String logEncode;
    private Pattern pattern;
    private String deteFormat;
    private String fields;

    public UnTailer(File file, LogFileTailerListener listener, long delayMillis, boolean end, boolean reOpen, int bufSize, String encode, Pattern pattern, String deteFormat, String fields) {
        this.run = true;
        this.file = file;
        this.delayMillis = delayMillis;
        this.end = end;
        this.inbuf = new byte[bufSize];
        this.logEncode = encode;
        this.listener = listener;
        listener.init(this);
        this.reOpen = reOpen;
        this.pattern = pattern;
        this.deteFormat = deteFormat;
        this.fields = fields;
    }

    public static UnTailer create(File file, LogFileTailerListener listener, long delayMillis, boolean end, boolean reOpen, int bufSize, String encode, Pattern pattern, String deteFormat, String fields) {
        UnTailer tailer = new UnTailer(file, listener, delayMillis, end, reOpen, bufSize, encode, pattern, deteFormat, fields);
        Thread thread = new Thread(tailer);
        thread.setDaemon(true);
        thread.start();
        return tailer;
    }

    public File getFile() {
        return this.file;
    }

    public long getDelay() {
        return this.delayMillis;
    }

    public void run() {
        RandomAccessFile reader = null;

        try {
            long last = 0L;
            long position = 0L;

            while(this.run && reader == null) {
                try {
                    reader = new RandomAccessFile(this.file, RAF_MODE);
                } catch (FileNotFoundException var20) {
                    this.listener.fileNotFound();
                }

                if (reader == null) {
                    try {
                        Thread.sleep(this.delayMillis);
                    } catch (InterruptedException var19) {
                        ;
                    }
                } else {
                    position = this.end ? this.file.length() : 0L;
                    last = System.currentTimeMillis();
                    reader.seek(position);
                }
            }

            while(this.run) {
                boolean newer = FileUtils.isFileNewer(this.file, last);
                long length = this.file.length();
                if (length < position) {
                    this.listener.fileRotated();

                    try {
                        RandomAccessFile save = reader;
                        reader = new RandomAccessFile(this.file, RAF_MODE);
                        position = 0L;
                        IOUtils.closeQuietly(save);
                    } catch (FileNotFoundException var18) {
                        this.listener.fileNotFound();
                    }
                } else {
                    if (length > position) {
                        position = this.readLines(reader);
                        last = System.currentTimeMillis();
                    } else if (newer) {
                        position = 0L;
                        reader.seek(position);
                        position = this.readLines(reader);
                        last = System.currentTimeMillis();
                    }

                    if (this.reOpen) {
                        IOUtils.closeQuietly(reader);
                    }

                    try {
                        Thread.sleep(this.delayMillis);
                    } catch (InterruptedException var17) {
                        ;
                    }

                    if (this.run && this.reOpen) {
                        reader = new RandomAccessFile(this.file, "r");
                        reader.seek(position);
                    }
                }
            }
        } catch (Exception var21) {
            this.listener.handle(var21.getMessage(), this.pattern, this.deteFormat, this.fields);
        } finally {
            IOUtils.closeQuietly(reader);
        }

    }

    public void stop() {
        this.run = false;
    }

    private long readLines(RandomAccessFile reader) throws IOException {
        long pos = reader.getFilePointer();
        long rePos = pos;

        int num;
        List<Byte> byteList = new ArrayList<Byte>();
        for(boolean seenCR = false; this.run && (num = reader.read(this.inbuf)) != -1; pos = reader.getFilePointer()) {
            for(int i = 0; i < num; ++i) {
                byte ch = this.inbuf[i];
                switch(ch) {
                    case '\n':
                        seenCR = false;
                        this.listener.handle(byteBufferToString(byteList), this.pattern, this.deteFormat, this.fields);
                        byteList.clear();
                        rePos = pos + (long)i + 1L;
                        break;
                    case '\r':
                        if (seenCR) {
                            byteList.add((byte)13);
                        }

                        seenCR = true;
                        break;
                    default:
                        if (seenCR) {
                            seenCR = false;
                            this.listener.handle(byteBufferToString(byteList), this.pattern, this.deteFormat, this.fields);
                            byteList.clear();
                            rePos = pos + (long)i + 1L;
                        }

                        byteList.add(ch);
                }
            }
        }

        reader.seek(rePos);
        return rePos;
    }

	private String byteBufferToString(List<Byte> byteList) {
        byte[] bytes = new byte[byteList.size()];
        for(int i=0;i<byteList.size();i++){
            bytes[i] = byteList.get(i);
        }
        String result = "";
        try {
            result = new String(bytes,this.logEncode);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

}
