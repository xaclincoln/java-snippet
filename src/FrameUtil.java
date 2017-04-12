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
    //对于控制帧
    //帧头长度固定为16字节
    //帧头第一个字节表示帧的类型，1表示传输配置文件
    //紧跟的4个字节表示正文的长度
    //前面的5个字节所有的帧都必须遵从，后面的11字节不同类型帧自己决定如何填充

    //对于数据帧
    //帧头长度固定为16字节
    //帧头第一个字节表示帧的类型
    //帧的正文长度固定为1024，但帧头的第1-4个字节表示实际的正文长度
    public static final int HeaderLength = 16;
    public static final int HashLength = 16;
    public static final int DataLength = 1024;
    public static final long ChunkSize = 10 * 1024 * 1024;

    public static final int FrameTypeXmlTransportConfig = 1;
    public static final int FrameTypeRetransmit = 2;
    public static final int FrameTypeXmlTransportConfigResponse = 3;
    public static final int FrameTypeData = 4;
    public static final int FrameTypeChunkValidationRequest = 5;
    public static final int FrameTypeChunkValidationResponse = 6;

    static final byte[] retransmitFrame = new byte[HeaderLength];

    static {
        retransmitFrame[0] = 1;
    }

    public static byte[] makeValidationRequestFrame() {
        byte[] frame = new byte[HeaderLength];
        frame[0] = FrameTypeChunkValidationRequest;
        return frame;
    }

    public static byte[] makeChunkValidationResponseFrame(boolean needRetransmit) {
        byte[] frame = new byte[HeaderLength];
        frame[0] = FrameTypeChunkValidationResponse;
        frame[1 + 4] = needRetransmit ? (byte) 1 : 0;
        return frame;
    }

    public static byte[] makeDataFrame() {
        byte[] dataFrame = new byte[HeaderLength + DataLength];
        //数据帧的长度固定，因此在头部直接指定，这样便于接收
        dataFrame[0] = 4;
        return dataFrame;
    }

    public static byte[] makeRetransmitFrame() {
        return retransmitFrame;
    }

    public static byte[] makeFileTransportConfigResponseFrame(long position) {
        byte[] transportConfigResponseFrame = new byte[HeaderLength];
        transportConfigResponseFrame[0] = 3;
        byte[] positionBytes = ByteBuffer.allocate(8).putLong(position).array();
        System.arraycopy(positionBytes, 0, transportConfigResponseFrame, 1 + 4, positionBytes.length);
        return transportConfigResponseFrame;
    }

    public static byte[] makeFileTransportConfigFrame(FileTransportConfig config) throws NoSuchAlgorithmException, IOException {
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

    public static int byteArrayToInt(byte[] b, int start) {
        return b[start + 3] & 0xFF |
                (b[start + 2] & 0xFF) << 8 |
                (b[start + 1] & 0xFF) << 16 |
                (b[start + 0] & 0xFF) << 24;
    }

    public static int byteArrayToLong(byte[] b, int start) {
        return b[start + 7] & 0xFF |
                (b[start + 6] & 0xFF) << 8 |
                (b[start + 5] & 0xFF) << 16 |
                (b[start + 4] & 0xFF) << 24 |
                (b[start + 3] & 0xFF) << 32 |
                (b[start + 2] & 0xFF) << 40 |
                (b[start + 1] & 0xFF) << 48 |
                (b[start + 0] & 0xFF) << 56;
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

        for (Iterator i = root.element("chunks").elementIterator("chunk"); i.hasNext(); ) {
            Element chunkElement = (Element) i.next();
            int ordinalNum = Integer.parseInt(chunkElement.attribute("ordinalNum").getValue());
            String md5Hash = chunkElement.attribute("md5Hash").getValue();
            result.chunks.add(new Chunk(ordinalNum, md5Hash));
        }

        return result;
    }

    static FileTransportConfig generateTransportConfig(String filePath) throws IOException, NoSuchAlgorithmException {

        FileTransportConfig config = new FileTransportConfig();
        File f = new File(filePath);
        config.fileName = f.getName();
        config.fileSize = f.length();
        //config.md5Hash = calcFileMd5(filePath);

        long chunkCount = config.fileSize / ChunkSize;
        if (chunkCount == 0) {
            config.chunks.add(new Chunk(0, config.md5Hash));
        } else {
            for (int i = 0; i < chunkCount; i++) {
                long startPosition = i * ChunkSize;
                config.chunks.add(new Chunk(i, calcFileMd5(filePath, startPosition, startPosition + ChunkSize - 1)));
            }
            if (config.fileSize - chunkCount * ChunkSize > 0) {
                config.chunks.add(new Chunk((int) chunkCount, calcFileMd5(filePath, chunkCount * ChunkSize, config.fileSize - 1)));
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
        is.close();
        return javax.xml.bind.DatatypeConverter.printHexBinary(md.digest());

    }
}
