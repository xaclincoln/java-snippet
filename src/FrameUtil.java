import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/**
 * Created by Administrator on 2017/4/8.
 */
public class FrameUtil {
    //帧头长度固定为16字节
    //帧头第一个字节表示帧的类型，1表示传输配置文件
    //紧跟的4个字节表示正文的长度
    //前面的5个字节所有的帧都必须遵从，后面的11字节不同类型帧自己决定如何填充
    static final int HeaderLength = 16;
    static final int HashLength = 16;

    static final byte[] retransFrame = new byte[HeaderLength];

    public  static byte[] makeRetransmitFrame()
    {
        retransFrame[0] = 1;
        return retransFrame;
    }

    public static byte[] makeFileTransportConfigFrame(String filePath) throws NoSuchAlgorithmException, IOException {
        FileTransportConfig config = generateTransportConfig(filePath);
        Document doc = fromConfigToXml(config);


        //紧跟的4个字节表示配置文件字节流的长度
        byte[] configFileBytes = doc.asXML().getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[HeaderLength];
        header[0] = 1;
        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(configFileBytes.length + HashLength).array();
        System.arraycopy(lengthBytes, 0, header, 1, lengthBytes.length);
        byte[] xmlLengthBytes = ByteBuffer.allocate(4).putInt(configFileBytes.length).array();
        System.arraycopy(xmlLengthBytes, 0, header, 1 + 4, xmlLengthBytes.length);

        byte[] frame = new byte[header.length + configFileBytes.length + HashLength];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(configFileBytes, 0, frame, header.length, configFileBytes.length);

        MessageDigest md = MessageDigest.getInstance("md5");
        md.update(frame, 0, frame.length - HashLength);
        byte[] md5Hash = md.digest();
        System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(md5Hash));
        System.arraycopy(md5Hash, 0, frame, header.length + configFileBytes.length, md5Hash.length);

        return frame;
    }

    static Document fromConfigToXml(FileTransportConfig config) {
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("root");
        root.addElement("file")
                .addAttribute("name", config.fileName)
                .addAttribute("size", Long.toString(config.fileSize));
        Element chunks = root.addElement("chunks");
        for (Chunk c : config.chunks) {
            chunks.addElement("chunk")
                    .addAttribute("ordinalNum", Integer.toString(c.ordinalNum))
                    .addAttribute("md5Hash", c.md5Hash);
        }
        return doc;
    }

    static FileTransportConfig ParseFileTransportConfigXml(String xml) throws DocumentException {
        Document document = DocumentHelper.parseText(xml);
        FileTransportConfig result = new FileTransportConfig();
        Element root = document.getRootElement();
        Element fileElement = root.element("file");
        result.fileName = fileElement.attribute("name").getValue();
        result.fileSize = Integer.parseInt(fileElement.attribute("size").getValue());

        for (Iterator i = root.element("chunks").elementIterator( "chunk" ); i.hasNext(); ) {
            Element chunkElement = (Element) i.next();
            int ordinalNum = Integer.parseInt(chunkElement.attribute("ordinalNum").getValue());
            String md5Hash = chunkElement.attribute("md5Hash").getValue();
            result.chunks.add(new Chunk(ordinalNum,md5Hash));
        }

        return result;
    }

    static FileTransportConfig generateTransportConfig(String filePath) throws IOException, NoSuchAlgorithmException {
        final long chunkSize = 10 * 1024 * 1024;
        FileTransportConfig config = new FileTransportConfig();
        File f = new File(filePath);
        config.fileName = f.getName();
        config.fileSize = f.length();
        //config.md5Hash = calcFileMd5(filePath);

        long chunkCount = config.fileSize / chunkSize;
        if (chunkCount == 0) {
            config.chunks.add(new Chunk(0, config.md5Hash));
        } else {
            for (int i = 0; i < chunkCount; i++) {
                long startPosition = i * chunkSize;
                config.chunks.add(new Chunk(i, calcFileMd5(filePath, startPosition, startPosition + chunkSize - 1)));
            }
            if (config.fileSize - chunkCount * chunkSize > 0) {
                config.chunks.add(new Chunk((int) chunkCount + 1, calcFileMd5(filePath, chunkCount * chunkSize, config.fileSize - 1)));
            }
        }

        return config;
    }

    static String calcFileMd5(String filePath, long startPosition, long endPosition) throws IOException, NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("md5");
        InputStream is = Files.newInputStream(Paths.get(filePath));
        is.skip(startPosition);

        DigestInputStream dis = new DigestInputStream(is, md);
        byte[] bytes = new byte[1024];
        int totalCount = 0;
        while (true) {
            long toRead = (endPosition - startPosition + 1) - totalCount;
            if (toRead > bytes.length) {
                toRead = bytes.length;
            }
            int count = dis.read(bytes, 0, (int) toRead);
            totalCount += count;
            if (count < toRead || totalCount == endPosition - startPosition + 1) {
                break;
            }
        }
        return javax.xml.bind.DatatypeConverter.printHexBinary(md.digest());

    }
}
