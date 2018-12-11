package org.flashdog.agent;

import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author hill.hu
 */
public class DogMain {
      public  static  void main(String[] args) {
          FileSystemXmlApplicationContext applicationContext=new FileSystemXmlApplicationContext("classpath:conf/spring.xml");
          LogFileTailerListener listener = applicationContext.getBean(LogFileTailerListener.class);

          String[] fileList = listener.getFileName().split(",");
          String[] patternList = listener.getPatternTxt().split("`");
          String[] dateFormatList = listener.getDateFormat().split("`");
          String[] fieldList = listener.getFields().split("`");
          String[] encodeList = listener.getLogEncode().split("`");
          if((fileList.length != patternList.length)||(fileList.length != dateFormatList.length)||(fileList.length != fieldList.length)||(fileList.length != encodeList.length)){
              System.out.println("程序配置不正确");
              return;
          }
          for(int i=0;i<fileList.length;i++) {
              String fileName = fileList[i];
              String patternTxt = patternList[i];
              String deteFormat = dateFormatList[i];
              String fields = fieldList[i];
              String encode = encodeList[i];
              File file = new File(fileName);
              if ("".equals(encode)) encode = "UTF-8";
              Pattern pattern = Pattern.compile(patternTxt);
              UnTailer.create(file, listener, 1000, true, true, 4096, encode, pattern, deteFormat, fields);
          }
      }
}
