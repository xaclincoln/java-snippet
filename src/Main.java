import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class Main {

    public static void main(String[] args) {
//        byte[] bytes = new byte[16];
//        try {
//            FrameUtil.makeFileTransportConfigFrame("d://initrd.lz");
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        FileTransportConfig config = new FileTransportConfig();
//        config.fileSize = 2000;
//        config.fileName = "lincoln.txt";
//        config.chunks.add(new Chunk(0, "9092002"));
//        config.chunks.add(new Chunk(1, "89039302"));
//        Document doc = FrameUtil.fromConfigToXml(config);
//
//        try {
//            FrameUtil.ParseFileTransportConfigXml(doc.asXML());
//        } catch (DocumentException e) {
//            e.printStackTrace();
//        }

        try {
            OutputStream file =  new FileOutputStream("linc.dat", false);
            file.write(500);
            file.flush();
            file.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        String lastChunkFileName = "abc.chunk0004";
//        System.out.println(
//                Integer.parseInt(lastChunkFileName.substring(lastChunkFileName.length() - 4, lastChunkFileName.length()))
//        );

        //System.out.println(String.format("%04d ",30));

        //printFileNameAndFileSize();
        //        FileTransportConfig config = generateTransportConfig("d://initrd.lz");
        //        Document doc = fromConfigToXml(config);
//        System.out.println(doc.asXML());
        //calcFileMd5();

    }


    static String calcFileMd5(String filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("md5");
            InputStream is = Files.newInputStream(Paths.get(filePath));

            DigestInputStream dis = new DigestInputStream(is, md);
            byte[] bytes = new byte[1024];
            while (true) {
                int count = dis.read(bytes, 0, bytes.length);
                if (count < bytes.length) {
                    break;
                }
            }
            return javax.xml.bind.DatatypeConverter.printHexBinary(md.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static void printFileNameAndFileSize() {
        File f = new File("./.idea/compiler.xml");
        System.out.println(f.length());
        System.out.println(f.getName());
    }

    static void WriteXmlToFile() {
        try {
            Document doc = GenerateXmlDoc();
            FileWriter writer = new FileWriter("authors.xml");
            doc.write(writer);
            writer.close();
            System.out.println(doc.asXML());
        } catch (IOException ex) {

        }
    }


    static Document GenerateXmlDoc() {
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("root");
        root.addElement("author")
                .addAttribute("name", "james")
                .addAttribute("location", "uk");
        root.addElement("author")
                .addAttribute("name", "bond")
                .addAttribute("location", "us");

        return doc;
    }
}
