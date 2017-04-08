import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Administrator on 2017/4/8.
 */
public class FileTransportClient {
    private String _ip;
    private int _port = 9000;
    private Socket _sock;

    FileTransportClient(String ip, int port) throws IOException {
        this._ip = ip;
        this._port = port;
        _sock = new Socket();

    }

    void Connect() throws IOException {
        _sock.connect(new InetSocketAddress(_ip, _port));
    }

    void SendFileTransportXmlConfig(String filePath) throws IOException, NoSuchAlgorithmException {
        OutputStream writer = _sock.getOutputStream();
        InputStream reader = _sock.getInputStream();
        byte[] frame = FrameUtil.makeFileTransportConfigFrame(filePath);
        byte[] response = repeatSendXmlConfigFrame(writer, reader, frame);
        if (response[0] == FrameUtil.FrameTypeXmlTransportConfigResponse) {
            //这里分为两种情况，文件不存在，从头开始传输，文件已存在，进行续传
            int startPosition = FrameUtil.byteArrayToInt(response, 5);
            System.out.println("config response received, start position: " + startPosition);
        } else {

        }

    }

    byte[] repeatSendXmlConfigFrame(OutputStream writer, InputStream reader, byte[] frame) throws IOException {
        while (true) {
            writer.write(frame, 0, frame.length);
            byte[] response = getResponse(reader);
            if (response[0] == FrameUtil.FrameTypeRetransmit) {
                continue;
            } else {
                return response;
            }
        }
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

}
