import org.dom4j.DocumentException;
import sun.net.www.protocol.file.FileURLConnection;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    FileTransportServer(String ip, int port) {
        _ip = ip;
        _port = port;

    }

    void start() throws IOException, NoSuchAlgorithmException, DocumentException {
        _sock = new ServerSocket();
        _sock.bind(new InetSocketAddress(_ip, _port));
        Socket client = _sock.accept();
        InputStream reader = client.getInputStream();
        byte[] header = new byte[16];
        int count = reader.read(header, 0, header.length);
        if (count == -1) {
            return;
        }
        if (header[0] == 1) {
            int bodyLength = byteArrayToInt(header, 1);
            byte[] body = new byte[bodyLength];
            reader.read(body, 0, bodyLength);
            int xmlConfigLength = byteArrayToInt(header, 5);
            MessageDigest md5 = MessageDigest.getInstance("md5");
            md5.update(header);
            md5.update(body, 0, xmlConfigLength);
            byte[] md5Hash = md5.digest();
            if (md5.isEqual(md5Hash, Arrays.copyOfRange(body, xmlConfigLength, body.length ))) {
                String xmlString = new String(body, 0, xmlConfigLength, "UTF-8");
                FileTransportConfig config = FrameUtil.ParseFileTransportConfigXml(xmlString);
                //判断文件是否已经存在
                System.out.println(config);
            } else {
                OutputStream writer = client.getOutputStream();
                writer.write(FrameUtil.makeRetransmitFrame());
            }


        }
    }

    public static void main(String[] args) {
        FileTransportServer server = new FileTransportServer("127.0.0.1", 9000);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
    }

    public static int byteArrayToInt(byte[] b, int start) {
        return b[start + 3] & 0xFF |
                (b[start + 2] & 0xFF) << 8 |
                (b[start + 1] & 0xFF) << 16 |
                (b[start + 0] & 0xFF) << 24;
    }


}
