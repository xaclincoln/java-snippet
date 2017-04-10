import org.dom4j.DocumentException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by Administrator on 2017/4/8.
 */
public class FileTransportServer {
    private String _ip;
    private int _port;
    private ServerSocket _sock;
    private final String receivePath = "./接收/";

    FileTransportServer(String ip, int port) {
        _ip = ip;
        _port = port;

    }

    void start() {

        try {
            _sock = new ServerSocket();
            _sock.bind(new InetSocketAddress(_ip, _port));
        } catch (IOException e) {
            return;
        }

        while (true) {
            try {
                Socket client = _sock.accept();
                InputStream reader = client.getInputStream();
                OutputStream writer = client.getOutputStream();

                //首先接收文件传输的XML配置文件
                byte[] response = getResponse(reader);
                boolean valid = validate(response);
                if (valid && response[0] == FrameUtil.FrameTypeXmlTransportConfig) {
                    int xmlConfigLength = FrameUtil.byteArrayToInt(response, 5);
                    String xmlString = new String(response, FrameUtil.HeaderLength, xmlConfigLength, "UTF-8");
                    FileTransportConfig config = FrameUtil.ParseFileTransportConfigXml(xmlString);
                    //判断文件是否已经存在，如果存在，告诉续传的位置
                    //这里暂不考虑续传问题
                    saveFileTransportConfig(xmlString, config.fileName);
                    writer.write(FrameUtil.makeFileTransportConfigResponseFrame(0));

                    //开始接收数据
                } else {
                    writer.write(FrameUtil.makeRetransmitFrame());
                }
            } catch (IOException ex) {

            } catch (NoSuchAlgorithmException ex) {
            } catch (DocumentException ex) {
            }

        }

    }

    boolean validate(byte[] frame) throws NoSuchAlgorithmException {
        int xmlConfigLength = FrameUtil.byteArrayToInt(frame, 5);
        MessageDigest md5 = MessageDigest.getInstance("md5");
        md5.update(frame, 0, xmlConfigLength + FrameUtil.HeaderLength);
        byte[] md5Hash = md5.digest();
        return md5.isEqual(md5Hash, Arrays.copyOfRange(frame, xmlConfigLength + FrameUtil.HeaderLength, frame.length));
    }

    byte[] getResponse(InputStream reader) throws IOException {
        byte[] header = new byte[FrameUtil.HeaderLength];
        reader.read(header, 0, header.length);
        int bodyLength = FrameUtil.byteArrayToInt(header, 1);
        if (bodyLength != 0) {
            byte[] result = new byte[header.length + bodyLength];
            reader.read(result, header.length, bodyLength);
            System.arraycopy(header, 0, result, 0, header.length);
            return result;
        } else {
            return header;
        }
    }

    void saveFileTransportConfig(String config, String fileName) throws IOException {
        File dir = new File(receivePath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        FileWriter writer = new FileWriter(receivePath + fileName);
        writer.append(config);
        writer.close();
    }


    public static void main(String[] args) {

        FileTransportServer server = new FileTransportServer("127.0.0.1", 9000);
        server.start();


        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
