import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class Main {

    public static void main(String[] args) {
        //GetFileSize();
        calcFileMd5();
        System.out.println("Hello World!");
    }

    static void calcFileMd5() {
        try {
            MessageDigest md = MessageDigest.getInstance("md5");
            InputStream is = Files.newInputStream(Paths.get("authors.xml"));

            DigestInputStream dis = new DigestInputStream(is, md);
            byte[] bytes = new byte[1024];
            while (true) {
                int count = dis.read(bytes, 0, bytes.length);
                if (count < bytes.length) {
                    break;
                }
            }
            System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(md.digest()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static void GetFileSize() {
        File f = new File("authors.xml");
        System.out.println(f.length());
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
